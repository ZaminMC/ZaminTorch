package net.zaminmc.torch.server.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The 1.8.8 chat formatting table (Formatting.java 1:1): the sixteen color
 * ids 0-15, the five modifiers, and RESET (id -1) — with the reference's own
 * condense-name lookups (every non-lowercase-letter stripped) and the
 * §-code strip walk.
 */
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

    /** The reference's own lookup table (the static block's fill). */
    private static final Map<String, Formatting> BY_NAME = new HashMap<>();

    /** The reference's CODE_PATTERN: a § followed by a formatting code, case-insensitive. */
    private static final Pattern CODE_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");

    private final String name;
    private final char code;
    private final boolean modifier;
    private final String key;
    private final int id;

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

    /** The lowercase enum name ("dark_blue") — also the JSON key's exact form. */
    public String getName() {
        return this.name().toLowerCase();
    }

    @Override
    public String toString() {
        return this.key;
    }

    /** Strips every §-code from the string (the reference's strip). */
    public static String strip(String s) {
        return s == null ? null : CODE_PATTERN.matcher(s).replaceAll("");
    }

    /** The reference's byName: condense-name lookup, null-friendly. */
    public static Formatting byName(String name) {
        return name == null ? null : BY_NAME.get(condenseName(name));
    }

    /** The reference's byId: negative ids answer RESET, unknown ids null. */
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

    /** The reference's getNames: the filtered lowercase list. */
    public static List<String> getNames(boolean colors, boolean modifiers) {
        List<String> list = new ArrayList<>();
        for (Formatting formatting : values()) {
            if ((!formatting.isColor() || colors) && (!formatting.isModifier() || modifiers)) {
                list.add(formatting.getName());
            }
        }
        return list;
    }

    /** The reference's own condense walk: lowercase, every non-[a-z] dropped. */
    static String condenseName(String name) {
        return name.toLowerCase().replaceAll("[^a-z]", "");
    }

    static {
        for (Formatting formatting : values()) {
            BY_NAME.put(condenseName(formatting.name), formatting);
        }
    }
}
