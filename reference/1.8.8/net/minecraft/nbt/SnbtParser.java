package net.minecraft.nbt;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SnbtParser {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Pattern PRIMITIVE_PATTERN = Pattern.compile("\\[[-+\\d|,\\s]+\\]");

    public static NbtCompound parse(String rawNbt) throws NbtException {
        rawNbt = rawNbt.trim();
        if (!rawNbt.startsWith("{")) {
            throw new NbtException("Invalid tag encountered, expected '{' as first char.");
        } else if (getTopElementCount(rawNbt) != 1) {
            throw new NbtException("Encountered multiple top tags, only one expected");
        } else {
            return (NbtCompound)parseNbtEntry("tag", rawNbt).getValue();
        }
    }

    static int getTopElementCount(String rawNbt) throws NbtException {
        int i = 0;
        boolean flag = false;
        Stack<Character> stack = new Stack<>();

        for (int j = 0; j < rawNbt.length(); j++) {
            char c0 = rawNbt.charAt(j);
            if (c0 == '"') {
                if (isEscaped(rawNbt, j)) {
                    if (!flag) {
                        throw new NbtException("Illegal use of \\\": " + rawNbt);
                    }
                } else {
                    flag = !flag;
                }
            } else if (!flag) {
                if (c0 != '{' && c0 != '[') {
                    if (c0 == '}' && (stack.isEmpty() || stack.pop() != '{')) {
                        throw new NbtException("Unbalanced curly brackets {}: " + rawNbt);
                    }

                    if (c0 == ']' && (stack.isEmpty() || stack.pop() != '[')) {
                        throw new NbtException("Unbalanced square brackets []: " + rawNbt);
                    }
                } else {
                    if (stack.isEmpty()) {
                        i++;
                    }

                    stack.push(c0);
                }
            }
        }

        if (flag) {
            throw new NbtException("Unbalanced quotation: " + rawNbt);
        }

        if (!stack.isEmpty()) {
            throw new NbtException("Unbalanced brackets: " + rawNbt);
        }

        if (i == 0 && !rawNbt.isEmpty()) {
            i = 1;
        }

        return i;
    }

    static SnbtParser.Entry parseNbtEntry(String... rawEntry) throws NbtException {
        return parseNbtEntry(rawEntry[0], rawEntry[1]);
    }

    static SnbtParser.Entry parseNbtEntry(String key, String rawElement) throws NbtException {
        rawElement = rawElement.trim();
        if (rawElement.startsWith("{")) {
            rawElement = rawElement.substring(1, rawElement.length() - 1);
            SnbtParser.CompoundEntry snbtparser$compoundentry = new SnbtParser.CompoundEntry(key);

            while (rawElement.length() > 0) {
                String s1 = getNextEntry(rawElement, true);
                if (s1.length() > 0) {
                    boolean flag1 = false;
                    snbtparser$compoundentry.entries.add(parseNbtEntry(s1, flag1));
                }

                if (rawElement.length() < s1.length() + 1) {
                    break;
                }

                char c1 = rawElement.charAt(s1.length());
                if (c1 != ',' && c1 != '{' && c1 != '}' && c1 != '[' && c1 != ']') {
                    throw new NbtException("Unexpected token '" + c1 + "' at: " + rawElement.substring(s1.length()));
                }

                rawElement = rawElement.substring(s1.length() + 1);
            }

            return snbtparser$compoundentry;
        } else if (rawElement.startsWith("[") && !PRIMITIVE_PATTERN.matcher(rawElement).matches()) {
            rawElement = rawElement.substring(1, rawElement.length() - 1);
            SnbtParser.ListEntry snbtparser$listentry = new SnbtParser.ListEntry(key);

            while (rawElement.length() > 0) {
                String s = getNextEntry(rawElement, false);
                if (s.length() > 0) {
                    boolean flag = true;
                    snbtparser$listentry.entries.add(parseNbtEntry(s, flag));
                }

                if (rawElement.length() < s.length() + 1) {
                    break;
                }

                char c0 = rawElement.charAt(s.length());
                if (c0 != ',' && c0 != '{' && c0 != '}' && c0 != '[' && c0 != ']') {
                    throw new NbtException("Unexpected token '" + c0 + "' at: " + rawElement.substring(s.length()));
                }

                rawElement = rawElement.substring(s.length() + 1);
            }

            return snbtparser$listentry;
        } else {
            return new SnbtParser.PrimitiveEntry(key, rawElement);
        }
    }

    private static SnbtParser.Entry parseNbtEntry(String rawEntry, boolean allowNoSeparator) throws NbtException {
        String s = getKey(rawEntry, allowNoSeparator);
        String s1 = getValue(rawEntry, allowNoSeparator);
        return parseNbtEntry(s, s1);
    }

    private static String getNextEntry(String rawNbt, boolean expectSeparator) throws NbtException {
        int i = findSeparator(rawNbt, ':');
        int j = findSeparator(rawNbt, ',');
        if (expectSeparator) {
            if (i == -1) {
                throw new NbtException("Unable to locate name/value separator for string: " + rawNbt);
            }

            if (j != -1 && j < i) {
                throw new NbtException("Name error at: " + rawNbt);
            }
        } else if (i == -1 || i > j) {
            i = -1;
        }

        return getNextEntry(rawNbt, i);
    }

    private static String getNextEntry(String rawNbt, int separatorIndex) throws NbtException {
        Stack<Character> stack = new Stack<>();
        int i = separatorIndex + 1;
        boolean flag = false;
        boolean flag1 = false;
        boolean flag2 = false;
        int j = 0;

        while (i < rawNbt.length()) {
            char c0 = rawNbt.charAt(i);
            if (c0 == '"') {
                if (isEscaped(rawNbt, i)) {
                    if (!flag) {
                        throw new NbtException("Illegal use of \\\": " + rawNbt);
                    }
                } else {
                    flag = !flag;
                    if (flag && !flag2) {
                        flag1 = true;
                    }

                    if (!flag) {
                        j = i;
                    }
                }
            } else if (!flag) {
                if (c0 != '{' && c0 != '[') {
                    if (c0 == '}' && (stack.isEmpty() || stack.pop() != '{')) {
                        throw new NbtException("Unbalanced curly brackets {}: " + rawNbt);
                    }

                    if (c0 == ']' && (stack.isEmpty() || stack.pop() != '[')) {
                        throw new NbtException("Unbalanced square brackets []: " + rawNbt);
                    }

                    if (c0 == ',' && stack.isEmpty()) {
                        return rawNbt.substring(0, i);
                    }
                } else {
                    stack.push(c0);
                }
            }

            if (!Character.isWhitespace(c0)) {
                if (!flag && flag1 && j != i) {
                    return rawNbt.substring(0, j + 1);
                }

                flag2 = true;
            }

            i++;
        }

        return rawNbt.substring(0, i);
    }

    private static String getKey(String rawEntry, boolean allowNoSeparator) throws NbtException {
        if (allowNoSeparator) {
            rawEntry = rawEntry.trim();
            if (rawEntry.startsWith("{") || rawEntry.startsWith("[")) {
                return "";
            }
        }

        int i = findSeparator(rawEntry, ':');
        if (i != -1) {
            return rawEntry.substring(0, i).trim();
        } else if (allowNoSeparator) {
            return "";
        } else {
            throw new NbtException("Unable to locate name/value separator for string: " + rawEntry);
        }
    }

    private static String getValue(String rawEntry, boolean allowNoSeparator) throws NbtException {
        if (allowNoSeparator) {
            rawEntry = rawEntry.trim();
            if (rawEntry.startsWith("{") || rawEntry.startsWith("[")) {
                return rawEntry;
            }
        }

        int i = findSeparator(rawEntry, ':');
        if (i != -1) {
            return rawEntry.substring(i + 1).trim();
        } else if (allowNoSeparator) {
            return rawEntry;
        } else {
            throw new NbtException("Unable to locate name/value separator for string: " + rawEntry);
        }
    }

    /**
     * Find a character that separates nbt elements. This is either the character that
     * separates key/value pairs or the character that separates elements in a list.
     * 
     * @return the index of the character
     */
    private static int findSeparator(String rawNbt, char chr) {
        int i = 0;
        boolean flag = true;

        while (i < rawNbt.length()) {
            char c0 = rawNbt.charAt(i);
            if (c0 == '"') {
                if (!isEscaped(rawNbt, i)) {
                    flag = !flag;
                }
            } else if (flag) {
                if (c0 == chr) {
                    return i;
                }

                if (c0 == '{' || c0 == '[') {
                    return -1;
                }
            }

            i++;
        }

        return -1;
    }

    private static boolean isEscaped(String rawNbt, int index) {
        return index > 0 && rawNbt.charAt(index - 1) == '\\' && !isEscaped(rawNbt, index - 1);
    }

    static class CompoundEntry extends SnbtParser.Entry {
        protected List<SnbtParser.Entry> entries = Lists.newArrayList();

        public CompoundEntry(String key) {
            this.key = key;
        }

        @Override
        public NbtElement getValue() throws NbtException {
            NbtCompound nbtcompound = new NbtCompound();

            for (SnbtParser.Entry snbtparser$entry : this.entries) {
                nbtcompound.put(snbtparser$entry.key, snbtparser$entry.getValue());
            }

            return nbtcompound;
        }
    }

    abstract static class Entry {
        protected String key;

        public abstract NbtElement getValue() throws NbtException;
    }

    static class ListEntry extends SnbtParser.Entry {
        protected List<SnbtParser.Entry> entries = Lists.newArrayList();

        public ListEntry(String key) {
            this.key = key;
        }

        @Override
        public NbtElement getValue() throws NbtException {
            NbtList nbtlist = new NbtList();

            for (SnbtParser.Entry snbtparser$entry : this.entries) {
                nbtlist.addElement(snbtparser$entry.getValue());
            }

            return nbtlist;
        }
    }

    static class PrimitiveEntry extends SnbtParser.Entry {
        private static final Pattern DOUBLE_WITH_SUFFIX_PATTERN = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+[d|D]");
        private static final Pattern FLOAT_PATTERN = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+[f|F]");
        private static final Pattern BYTE_PATTERN = Pattern.compile("[-+]?[0-9]+[b|B]");
        private static final Pattern LONG_PATTERN = Pattern.compile("[-+]?[0-9]+[l|L]");
        private static final Pattern SHORT_PATTERN = Pattern.compile("[-+]?[0-9]+[s|S]");
        private static final Pattern INT_PATTERN = Pattern.compile("[-+]?[0-9]+");
        private static final Pattern DOUBLE_PATTERN = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+");
        private static final Splitter LIST_ELEMENT_SPLITTER = Splitter.on(',').omitEmptyStrings();
        protected String rawValue;

        public PrimitiveEntry(String key, String rawValue) {
            this.key = key;
            this.rawValue = rawValue;
        }

        @Override
        public NbtElement getValue() throws NbtException {
            try {
                if (DOUBLE_WITH_SUFFIX_PATTERN.matcher(this.rawValue).matches()) {
                    return new NbtDouble(Double.parseDouble(this.rawValue.substring(0, this.rawValue.length() - 1)));
                }

                if (FLOAT_PATTERN.matcher(this.rawValue).matches()) {
                    return new NbtFloat(Float.parseFloat(this.rawValue.substring(0, this.rawValue.length() - 1)));
                }

                if (BYTE_PATTERN.matcher(this.rawValue).matches()) {
                    return new NbtByte(Byte.parseByte(this.rawValue.substring(0, this.rawValue.length() - 1)));
                }

                if (LONG_PATTERN.matcher(this.rawValue).matches()) {
                    return new NbtLong(Long.parseLong(this.rawValue.substring(0, this.rawValue.length() - 1)));
                }

                if (SHORT_PATTERN.matcher(this.rawValue).matches()) {
                    return new NbtShort(Short.parseShort(this.rawValue.substring(0, this.rawValue.length() - 1)));
                }

                if (INT_PATTERN.matcher(this.rawValue).matches()) {
                    return new NbtInt(Integer.parseInt(this.rawValue));
                }

                if (DOUBLE_PATTERN.matcher(this.rawValue).matches()) {
                    return new NbtDouble(Double.parseDouble(this.rawValue));
                }

                if (this.rawValue.equalsIgnoreCase("true") || this.rawValue.equalsIgnoreCase("false")) {
                    return new NbtByte((byte)(Boolean.parseBoolean(this.rawValue) ? 1 : 0));
                }
            } catch (NumberFormatException numberformatexception1) {
                this.rawValue = this.rawValue.replaceAll("\\\\\"", "\"");
                return new NbtString(this.rawValue);
            }

            if (this.rawValue.startsWith("[") && this.rawValue.endsWith("]")) {
                String s = this.rawValue.substring(1, this.rawValue.length() - 1);
                String[] astring = Iterables.toArray(LIST_ELEMENT_SPLITTER.split(s), String.class);

                try {
                    int[] aint = new int[astring.length];

                    for (int j = 0; j < astring.length; j++) {
                        aint[j] = Integer.parseInt(astring[j].trim());
                    }

                    return new NbtIntArray(aint);
                } catch (NumberFormatException numberformatexception) {
                    return new NbtString(this.rawValue);
                }
            } else {
                if (this.rawValue.startsWith("\"") && this.rawValue.endsWith("\"")) {
                    this.rawValue = this.rawValue.substring(1, this.rawValue.length() - 1);
                }

                this.rawValue = this.rawValue.replaceAll("\\\\\"", "\"");
                StringBuilder stringbuilder = new StringBuilder();

                for (int i = 0; i < this.rawValue.length(); i++) {
                    if (i < this.rawValue.length() - 1 && this.rawValue.charAt(i) == '\\' && this.rawValue.charAt(i + 1) == '\\') {
                        stringbuilder.append('\\');
                        i++;
                    } else {
                        stringbuilder.append(this.rawValue.charAt(i));
                    }
                }

                return new NbtString(stringbuilder.toString());
            }
        }
    }
}
