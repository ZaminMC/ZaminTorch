package net.minecraft.locale;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.InputStream;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

public class Language {
    private static final Pattern ILLEGAL_CHARACTERS_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d\\.]*[df]");
    private static final Splitter TRANSLATION_SPLITTER = Splitter.on('=').limit(2);
    private static Language INSTANCE = new Language();
    private final Map<String, String> translations = Maps.newHashMap();
    private long loadTime;

    public Language() {
        try {
            InputStream inputstream = Language.class.getResourceAsStream("/assets/minecraft/lang/en_US.lang");

            for (String s : IOUtils.readLines(inputstream, Charsets.UTF_8)) {
                if (!s.isEmpty() && s.charAt(0) != '#') {
                    String[] astring = Iterables.toArray(TRANSLATION_SPLITTER.split(s), String.class);
                    if (astring != null && astring.length == 2) {
                        String s1 = astring[0];
                        String s2 = ILLEGAL_CHARACTERS_PATTERN.matcher(astring[1]).replaceAll("%$1s");
                        this.translations.put(s1, s2);
                    }
                }
            }

            this.loadTime = System.currentTimeMillis();
        } catch (IOException ioexception) {
        }
    }

    static Language getInstance() {
        return INSTANCE;
    }

    public static synchronized void setTranslations(Map<String, String> translations) {
        INSTANCE.translations.clear();
        INSTANCE.translations.putAll(translations);
        INSTANCE.loadTime = System.currentTimeMillis();
    }

    public synchronized String translate(String key) {
        return this.translateKey(key);
    }

    public synchronized String translate(String key, Object... args) {
        String s = this.translateKey(key);

        try {
            return String.format(s, args);
        } catch (IllegalFormatException illegalformatexception) {
            return "Format error: " + s;
        }
    }

    private String translateKey(String key) {
        String s = this.translations.get(key);
        return s == null ? key : s;
    }

    public synchronized boolean hasTranslation(String key) {
        return this.translations.containsKey(key);
    }

    public long getLoadTime() {
        return this.loadTime;
    }
}
