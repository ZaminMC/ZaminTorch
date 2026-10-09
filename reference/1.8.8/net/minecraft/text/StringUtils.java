package net.minecraft.text;

import java.util.regex.Pattern;

public class StringUtils {
    private static final Pattern PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");

    public static String getDurationString(int ticks) {
        int i = ticks / 20;
        int j = i / 60;
        i %= 60;
        return i < 10 ? j + ":0" + i : j + ":" + i;
    }

    public static String stripFormatting(String text) {
        return PATTERN.matcher(text).replaceAll("");
    }

    public static boolean isStringEmpty(String string) {
        return org.apache.commons.lang3.StringUtils.isEmpty(string);
    }
}
