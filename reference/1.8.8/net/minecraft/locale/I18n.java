package net.minecraft.locale;

public class I18n {
    private static Language LANGUAGE = Language.getInstance();
    private static Language DEFAULT_LANGUAGE = new Language();

    public static String translate(String key) {
        return LANGUAGE.translate(key);
    }

    public static String translate(String key, Object... args) {
        return LANGUAGE.translate(key, args);
    }

    public static String translateDefault(String key) {
        return DEFAULT_LANGUAGE.translate(key);
    }

    public static boolean hasTranslation(String key) {
        return LANGUAGE.hasTranslation(key);
    }

    public static long getLoadTime() {
        return LANGUAGE.getLoadTime();
    }
}
