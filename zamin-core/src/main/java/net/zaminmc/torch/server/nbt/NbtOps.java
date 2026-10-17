package net.zaminmc.torch.server.nbt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The compound operations over the engine's one typed value model (Slice 89
 * — the {@code NbtCompound}/{@code NbtUtils}/{@code NbtElement} surface
 * ported whole onto {@link Snbt.Tag}): the deep copy, the deep equality,
 * the reference's own recursive {@code merge}, the {@code NbtUtils.matches}
 * subset check, the SNBT writer ({@code NbtCompound.toString} with the
 * per-type suffix forms), and the getter/putter family with the reference's
 * exact coercion contracts — every numeric getter accepts ANY numeric
 * family and widens ({@code contains(key, 99)}), the string getter is
 * STRING-only, {@code getBoolean} is {@code getByte != 0}, and
 * {@code getList(key, type)} answers empty for a missing row, a non-list
 * row, or an element-type mismatch on a non-empty list.
 *
 * <p><b>The writer's per-type forms</b> (the reference's own
 * {@code toString} bodies): byte {@code 3b}, short {@code 5s}, int
 * {@code 6}, long {@code 4L}, float {@code 2.5f}, double {@code 1.5d},
 * string {@code "quoted"} with {@code "} escaped as {@code \"}, list
 * {@code [0:v0,1:v1]} (the index-colon form), compound {@code {k:v,...}},
 * int array {@code [1,2,]} (the trailing-comma form).</p>
 */
public final class NbtOps {

    private NbtOps() {
    }

    // ------------------------------------------------------------------
    // The putter family (the reference's putX over the engine's Tag)
    // ------------------------------------------------------------------

    public static void putByte(Map<String, Snbt.Tag> rows, String key, byte value) {
        rows.put(key, Snbt.Tag.ofByte(value));
    }

    public static void putShort(Map<String, Snbt.Tag> rows, String key, short value) {
        rows.put(key, Snbt.Tag.ofShort(value));
    }

    public static void putInt(Map<String, Snbt.Tag> rows, String key, int value) {
        rows.put(key, Snbt.Tag.ofInt(value));
    }

    public static void putLong(Map<String, Snbt.Tag> rows, String key, long value) {
        rows.put(key, Snbt.Tag.ofLong(value));
    }

    public static void putFloat(Map<String, Snbt.Tag> rows, String key, float value) {
        rows.put(key, Snbt.Tag.ofFloat(value));
    }

    public static void putDouble(Map<String, Snbt.Tag> rows, String key, double value) {
        rows.put(key, Snbt.Tag.ofDouble(value));
    }

    public static void putString(Map<String, Snbt.Tag> rows, String key, String value) {
        rows.put(key, Snbt.Tag.ofString(value));
    }

    /** The reference's putBoolean: the byte 1/0. */
    public static void putBoolean(Map<String, Snbt.Tag> rows, String key, boolean value) {
        rows.put(key, Snbt.Tag.ofByte((byte) (value ? 1 : 0)));
    }

    public static void putList(Map<String, Snbt.Tag> rows, String key, List<Snbt.Tag> value) {
        rows.put(key, Snbt.Tag.ofList(new ArrayList<>(value)));
    }

    public static void putCompound(Map<String, Snbt.Tag> rows, String key, Map<String, Snbt.Tag> value) {
        rows.put(key, Snbt.Tag.ofCompound(value));
    }

    // ------------------------------------------------------------------
    // The list builders (the reference's toNbtList forms)
    // ------------------------------------------------------------------

    /** The Pos/Motion form: a list of DOUBLEs. */
    public static List<Snbt.Tag> doubleList(double... values) {
        List<Snbt.Tag> out = new ArrayList<>(values.length);
        for (double value : values) {
            out.add(Snbt.Tag.ofDouble(value));
        }
        return out;
    }

    /** The Rotation form: a list of FLOATs. */
    public static List<Snbt.Tag> floatList(float... values) {
        List<Snbt.Tag> out = new ArrayList<>(values.length);
        for (float value : values) {
            out.add(Snbt.Tag.ofFloat(value));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // The getter family (the reference's coercion contracts)
    // ------------------------------------------------------------------

    /** @return the row, or null when absent. */
    public static Snbt.Tag get(Map<String, Snbt.Tag> rows, String key) {
        return rows.get(key);
    }

    /** The numeric-family gate (the reference's contains(key, 99)). */
    private static boolean isNumeric(Snbt.Tag tag) {
        if (tag == null) {
            return false;
        }
        return switch (tag.type()) {
            case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> true;
            default -> false;
        };
    }

    /** The reference's contains(key, 99): any numeric family at the key. */
    public static boolean containsNumeric(Map<String, Snbt.Tag> rows, String key) {
        return isNumeric(rows.get(key));
    }

    /** The reference's contains(key, type). */
    public static boolean contains(Map<String, Snbt.Tag> rows, String key, Snbt.Tag.Type type) {
        Snbt.Tag tag = rows.get(key);
        return tag != null && tag.type() == type;
    }

    /**
     * The numeric getters: ANY numeric family widens (the reference's
     * {@code contains(key, 99)} then {@code Number.getX}); a non-numeric
     * or absent row answers the type's zero.
     */
    public static byte getByte(Map<String, Snbt.Tag> rows, String key) {
        Snbt.Tag tag = rows.get(key);
        return isNumeric(tag) ? (byte) tag.asDouble() : 0;
    }

    public static short getShort(Map<String, Snbt.Tag> rows, String key) {
        Snbt.Tag tag = rows.get(key);
        return isNumeric(tag) ? (short) tag.asDouble() : 0;
    }

    public static int getInt(Map<String, Snbt.Tag> rows, String key) {
        Snbt.Tag tag = rows.get(key);
        return isNumeric(tag) ? (int) tag.asDouble() : 0;
    }

    public static long getLong(Map<String, Snbt.Tag> rows, String key) {
        Snbt.Tag tag = rows.get(key);
        return isNumeric(tag) ? tag.asLong() : 0L;
    }

    public static float getFloat(Map<String, Snbt.Tag> rows, String key) {
        Snbt.Tag tag = rows.get(key);
        return isNumeric(tag) ? (float) tag.asDouble() : 0.0f;
    }

    public static double getDouble(Map<String, Snbt.Tag> rows, String key) {
        Snbt.Tag tag = rows.get(key);
        return isNumeric(tag) ? tag.asDouble() : 0.0;
    }

    /** STRING-only (the reference's contains(key, 8)); else the empty string. */
    public static String getString(Map<String, Snbt.Tag> rows, String key) {
        Snbt.Tag tag = rows.get(key);
        return tag != null && tag.type() == Snbt.Tag.Type.STRING ? tag.asString() : "";
    }

    /** The reference's getBoolean: {@code getByte != 0} (any numeric). */
    public static boolean getBoolean(Map<String, Snbt.Tag> rows, String key) {
        return getByte(rows, key) != 0;
    }

    /** The reference's getCompound: COMPOUND-only; else an empty map. */
    public static Map<String, Snbt.Tag> getCompound(Map<String, Snbt.Tag> rows, String key) {
        Snbt.Tag tag = rows.get(key);
        return tag != null && tag.type() == Snbt.Tag.Type.COMPOUND
                ? tag.asCompound() : new LinkedHashMap<>();
    }

    /**
     * The reference's getList(key, type): empty for a missing row, a
     * non-list row, or an element-family mismatch on a non-empty list (the
     * first element's family stands in for the stored element type).
     */
    public static List<Snbt.Tag> getList(Map<String, Snbt.Tag> rows, String key, Snbt.Tag.Type expected) {
        Snbt.Tag tag = rows.get(key);
        if (tag == null || tag.type() != Snbt.Tag.Type.LIST) {
            return new ArrayList<>();
        }
        List<Snbt.Tag> elements = tag.asList();
        if (elements == null) {
            return new ArrayList<>();
        }
        if (!elements.isEmpty() && elements.get(0).type() != expected) {
            return new ArrayList<>();
        }
        return elements;
    }

    // ------------------------------------------------------------------
    // The list element accessors (the reference's NbtList.getX: 0 outside)
    // ------------------------------------------------------------------

    /** The reference's NbtList.getDouble: 0.0 out of bounds or wrong family. */
    public static double listDouble(List<Snbt.Tag> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return 0.0;
        }
        Snbt.Tag tag = list.get(index);
        return tag != null && tag.type() == Snbt.Tag.Type.DOUBLE ? tag.asDouble() : 0.0;
    }

    /** The reference's NbtList.getFloat: 0.0f out of bounds or wrong family. */
    public static float listFloat(List<Snbt.Tag> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return 0.0f;
        }
        Snbt.Tag tag = list.get(index);
        return tag != null && tag.type() == Snbt.Tag.Type.FLOAT ? tag.asFloat() : 0.0f;
    }

    /** The reference's NbtList.getCompound: an empty map out of bounds or wrong family. */
    public static Map<String, Snbt.Tag> listCompound(List<Snbt.Tag> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return new LinkedHashMap<>();
        }
        Snbt.Tag tag = list.get(index);
        return tag != null && tag.type() == Snbt.Tag.Type.COMPOUND
                ? tag.asCompound() : new LinkedHashMap<>();
    }

    // ------------------------------------------------------------------
    // The copy / equality / merge / matches (the reference's own bodies)
    // ------------------------------------------------------------------

    /** The reference's NbtCompound.copy: the deep copy. */
    public static Map<String, Snbt.Tag> copy(Map<String, Snbt.Tag> from) {
        Map<String, Snbt.Tag> out = new LinkedHashMap<>();
        if (from == null) {
            return out;
        }
        for (Map.Entry<String, Snbt.Tag> row : from.entrySet()) {
            out.put(row.getKey(), copyElement(row.getValue()));
        }
        return out;
    }

    /** The element deep copy (the reference's per-type copy). */
    public static Snbt.Tag copyElement(Snbt.Tag tag) {
        if (tag == null) {
            return null;
        }
        return switch (tag.type()) {
            case BYTE -> Snbt.Tag.ofByte(tag.asByte());
            case SHORT -> Snbt.Tag.ofShort((short) widen(tag));
            case INT -> Snbt.Tag.ofInt((int) widen(tag));
            case LONG -> Snbt.Tag.ofLong(tag.asLong());
            case FLOAT -> Snbt.Tag.ofFloat((float) widen(tag));
            case DOUBLE -> Snbt.Tag.ofDouble(tag.asDouble());
            case STRING -> Snbt.Tag.ofString(tag.asString());
            case INT_ARRAY -> {
                int[] rows = tag.asIntArray();
                yield Snbt.Tag.ofIntArray(rows == null ? new int[0] : rows.clone());
            }
            case LIST -> {
                List<Snbt.Tag> elements = tag.asList();
                List<Snbt.Tag> copied = new ArrayList<>();
                if (elements != null) {
                    for (Snbt.Tag element : elements) {
                        copied.add(copyElement(element));
                    }
                }
                yield Snbt.Tag.ofList(copied);
            }
            case COMPOUND -> Snbt.Tag.ofCompound(copy(tag.asCompound()));
        };
    }

    private static double widen(Snbt.Tag tag) {
        return tag.asDouble();
    }

    /**
     * The deep equality (the reference's entrySet equals): compounds compare
     * their key rows with deep-equal values (order-insensitive, the map
     * contract); lists compare element-by-element in order; primitives by
     * family then value.
     */
    public static boolean equal(Map<String, Snbt.Tag> a, Map<String, Snbt.Tag> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<String, Snbt.Tag> row : a.entrySet()) {
            if (!b.containsKey(row.getKey())) {
                return false;
            }
            if (!equalElements(row.getValue(), b.get(row.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** The element equality (the reference's per-type equals). */
    public static boolean equalElements(Snbt.Tag a, Snbt.Tag b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.type() != b.type()) {
            return false;
        }
        return switch (a.type()) {
            case STRING -> a.asString().equals(b.asString());
            case INT_ARRAY -> {
                int[] left = a.asIntArray();
                int[] right = b.asIntArray();
                yield left != null && right != null && java.util.Arrays.equals(left, right);
            }
            case LIST -> {
                List<Snbt.Tag> left = a.asList();
                List<Snbt.Tag> right = b.asList();
                if (left == null || right == null || left.size() != right.size()) {
                    yield false;
                }
                for (int i = 0; i < left.size(); i++) {
                    if (!equalElements(left.get(i), right.get(i))) {
                        yield false;
                    }
                }
                yield true;
            }
            case COMPOUND -> equal(a.asCompound(), b.asCompound());
            case LONG -> a.asLong() == b.asLong();
            default -> a.asDouble() == b.asDouble();
        };
    }

    /**
     * The reference's NbtCompound.merge: every row of {@code from} lands in
     * {@code into} — a compound row merges recursively into the same-key
     * compound, everything else replaces by deep copy.
     */
    public static void merge(Map<String, Snbt.Tag> into, Map<String, Snbt.Tag> from) {
        if (into == null || from == null) {
            return;
        }
        for (Map.Entry<String, Snbt.Tag> row : from.entrySet()) {
            Snbt.Tag value = row.getValue();
            if (value != null && value.type() == Snbt.Tag.Type.COMPOUND
                    && into.get(row.getKey()) != null
                    && into.get(row.getKey()).type() == Snbt.Tag.Type.COMPOUND) {
                merge(into.get(row.getKey()).asCompound(), value.asCompound());
            } else {
                into.put(row.getKey(), copyElement(value));
            }
        }
    }

    /**
     * The reference's NbtUtils.matches (the subset check): the pattern's
     * every row must be present-and-equal in the actual — a missing row
     * fails, an extra actual row passes, lists recurse element-wise when
     * {@code recurseLists} (each pattern element needs SOME equal actual
     * element; an empty pattern list matches only an empty actual list),
     * compounds walk their rows, everything else is the deep equals.
     */
    public static boolean matches(Snbt.Tag pattern, Snbt.Tag actual, boolean recurseLists) {
        if (pattern == actual) {
            return true;
        }
        if (pattern == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (pattern.type() != actual.type()) {
            return false;
        }
        if (pattern.type() == Snbt.Tag.Type.COMPOUND) {
            Map<String, Snbt.Tag> patternRows = pattern.asCompound();
            Map<String, Snbt.Tag> actualRows = actual.asCompound();
            for (Map.Entry<String, Snbt.Tag> row : patternRows.entrySet()) {
                Snbt.Tag actualRow = actualRows == null ? null : actualRows.get(row.getKey());
                if (!matches(row.getValue(), actualRow, recurseLists)) {
                    return false;
                }
            }
            return true;
        }
        if (pattern.type() == Snbt.Tag.Type.LIST && recurseLists) {
            List<Snbt.Tag> patternList = pattern.asList();
            List<Snbt.Tag> actualList = actual.asList();
            if (patternList == null || actualList == null) {
                return patternList == actualList;
            }
            if (patternList.isEmpty()) {
                return actualList.isEmpty();
            }
            for (Snbt.Tag patternElement : patternList) {
                boolean found = false;
                for (Snbt.Tag actualElement : actualList) {
                    if (matches(patternElement, actualElement, recurseLists)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
        return equalElements(pattern, actual);
    }

    // ------------------------------------------------------------------
    // The SNBT writer (the reference's toString bodies)
    // ------------------------------------------------------------------

    /** The reference's NbtCompound.toString: {@code {k:v,k:v}}. */
    public static String write(Map<String, Snbt.Tag> rows) {
        StringBuilder out = new StringBuilder("{");
        if (rows != null) {
            boolean first = true;
            for (Map.Entry<String, Snbt.Tag> row : rows.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(row.getKey()).append(':').append(writeElement(row.getValue()));
            }
        }
        return out.append('}').toString();
    }

    /** The per-type toString forms (byte {@code 3b} .. int array {@code [1,2,]}). */
    public static String writeElement(Snbt.Tag tag) {
        if (tag == null) {
            return "";
        }
        return switch (tag.type()) {
            case BYTE -> tag.asByte() + "b";
            case SHORT -> (short) tag.asDouble() + "s";
            case INT -> (int) tag.asDouble() + "";
            case LONG -> tag.asLong() + "L";
            case FLOAT -> (float) tag.asDouble() + "f";
            case DOUBLE -> tag.asDouble() + "d";
            case STRING -> "\"" + tag.asString().replace("\"", "\\\"") + "\"";
            case INT_ARRAY -> {
                int[] values = tag.asIntArray();
                StringBuilder out = new StringBuilder("[");
                if (values != null) {
                    for (int value : values) {
                        out.append(value).append(',');
                    }
                }
                yield out.append(']').toString();
            }
            case LIST -> {
                List<Snbt.Tag> elements = tag.asList();
                StringBuilder out = new StringBuilder("[");
                if (elements != null) {
                    for (int i = 0; i < elements.size(); i++) {
                        if (i != 0) {
                            out.append(',');
                        }
                        out.append(i).append(':').append(writeElement(elements.get(i)));
                    }
                }
                yield out.append(']').toString();
            }
            case COMPOUND -> write(tag.asCompound());
        };
    }
}
