package net.minecraft.text;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public enum Formatting {
    BLACK("BLACK", '0', 0),
    DARK_BLUE("DARK_BLUE", '1', 1),
    DARK_GREEN("DARK_GREEN", '2', 2),
    DARK_AQUA("DARK_AQUA", '3', 3),
    DARK_RED("DARK_RED", '4', 4),
    DARK_PURPLE("DARK_PURPLE", '5', 5),
    GOLD("GOLD", '6', 6),
    GRAY("GRAY", '7', 7),
    DARK_GRAY("DARK_GRAY", '8', 8),
    BLUE("BLUE", '9', 9),
    GREEN("GREEN", 'a', 10),
    AQUA("AQUA", 'b', 11),
    RED("RED", 'c', 12),
    LIGHT_PURPLE("LIGHT_PURPLE", 'd', 13),
    YELLOW("YELLOW", 'e', 14),
    WHITE("WHITE", 'f', 15),
    OBFUSCATED("OBFUSCATED", 'k', true),
    BOLD("BOLD", 'l', true),
    STRIKETHROUGH("STRIKETHROUGH", 'm', true),
    UNDERLINE("UNDERLINE", 'n', true),
    ITALIC("ITALIC", 'o', true),
    RESET("RESET", 'r', -1);

    private static final Map<String, Formatting> BY_NAME = Maps.newHashMap();
    private static final Pattern CODE_PATTERN = Pattern.compile("(?i)" + String.valueOf('§') + "[0-9A-FK-OR]");
    private final String name;
    private final char code;
    private final boolean modifier;
    private final String key;
    private final int id;

    private static String condenseName(String name) {
        return name.toLowerCase().replaceAll("[^a-z]", "");
    }

    Formatting(String name, char code, int id) {
        this(name, code, false, id);
    }

    Formatting(String name, char code, boolean modifier) {
        this(name, code, modifier, -1);
    }

    Formatting(String name, char code, boolean modifier, int id) {
        this.name = name;
        this.code = code;
        this.modifier = modifier;
        this.id = id;
        this.key = "§" + code;
    }

    public int getId() {
        return this.id;
    }

    public boolean isModifier() {
        return this.modifier;
    }

    public boolean isColor() {
        return !this.modifier && this != RESET;
    }

    public String getName() {
        return this.name().toLowerCase();
    }

    @Override
    public String toString() {
        return this.key;
    }

    public static String strip(String s) {
        return s == null ? null : CODE_PATTERN.matcher(s).replaceAll("");
    }

    public static Formatting byName(String name) {
        return name == null ? null : BY_NAME.get(condenseName(name));
    }

    public static Formatting byId(int id) {
        if (id < 0) {
            return RESET;
        }

        for (Formatting formatting : values()) {
            if (formatting.getId() == id) {
                return formatting;
            }
        }

        return null;
    }

    public static Collection<String> getNames(boolean colors, boolean modifiers) {
        List<String> list = Lists.newArrayList();

        for (Formatting formatting : values()) {
            if ((!formatting.isColor() || colors) && (!formatting.isModifier() || modifiers)) {
                list.add(formatting.getName());
            }
        }

        return list;
    }

    static {
        for (Formatting formatting : values()) {
            BY_NAME.put(condenseName(formatting.name), formatting);
        }
    }
}
