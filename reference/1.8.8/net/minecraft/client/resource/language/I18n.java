package net.minecraft.client.resource.language;

public class I18n {
    private static Locale locale;

    static void setLocale(Locale locale) {
        I18n.locale = locale;
    }

    public static String translate(String key, Object... args) {
        return locale.translate(key, args);
    }
}
