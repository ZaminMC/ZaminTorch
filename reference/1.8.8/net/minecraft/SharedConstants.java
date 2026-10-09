package net.minecraft;

public class SharedConstants {
    public static final char[] INVALID_FILE_CHARS = new char[]{'/', '\n', '\r', '\t', '\u0000', '\f', '`', '?', '*', '\\', '<', '>', '|', '"', ':'};

    public static boolean isValidChatChar(char chr) {
        return chr != 167 && chr >= ' ' && chr != 127;
    }

    public static String stripInvalidChars(String s) {
        StringBuilder stringbuilder = new StringBuilder();

        for (char c0 : s.toCharArray()) {
            if (isValidChatChar(c0)) {
                stringbuilder.append(c0);
            }
        }

        return stringbuilder.toString();
    }
}
