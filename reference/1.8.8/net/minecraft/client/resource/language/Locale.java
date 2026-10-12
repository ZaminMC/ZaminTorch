package net.minecraft.client.resource.language;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.InputStream;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

public class Locale {
    private static final Splitter SPLITTER = Splitter.on('=').limit(2);
    private static final Pattern ILLEGAL_CHARACTERS_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d\\.]*[df]");
    Map<String, String> translations = Maps.newHashMap();
    private boolean unicode;

    public synchronized void load(ResourceManager resourceManager, List<String> languageCodes) {
        this.translations.clear();

        for (String s : languageCodes) {
            String s1 = String.format("lang/%s.lang", s);

            for (String s2 : resourceManager.getNamespaces()) {
                try {
                    this.load(resourceManager.getResources(new Identifier(s2, s1)));
                } catch (IOException ioexception) {
                }
            }
        }

        this.checkUnicodeUsage();
    }

    public boolean isUnicode() {
        return this.unicode;
    }

    private void checkUnicodeUsage() {
        this.unicode = false;
        int i = 0;
        int j = 0;

        for (String s : this.translations.values()) {
            int k = s.length();
            j += k;

            for (int l = 0; l < k; l++) {
                if (s.charAt(l) >= 256) {
                    i++;
                }
            }
        }

        float f = (float)i / j;
        this.unicode = f > 0.1;
    }

    private void load(List<Resource> resources) throws IOException {
        for (Resource resource : resources) {
            InputStream inputstream = resource.asStream();

            try {
                this.load(inputstream);
            } finally {
                IOUtils.closeQuietly(inputstream);
            }
        }
    }

    private void load(InputStream is) throws IOException {
        for (String s : IOUtils.readLines(is, Charsets.UTF_8)) {
            if (!s.isEmpty() && s.charAt(0) != '#') {
                String[] astring = Iterables.toArray(SPLITTER.split(s), String.class);
                if (astring != null && astring.length == 2) {
                    String s1 = astring[0];
                    String s2 = ILLEGAL_CHARACTERS_PATTERN.matcher(astring[1]).replaceAll("%$1s");
                    this.translations.put(s1, s2);
                }
            }
        }
    }

    private String getTranslationOrKey(String key) {
        String s = this.translations.get(key);
        return s == null ? key : s;
    }

    public String translate(String key, Object[] args) {
        String s = this.getTranslationOrKey(key);

        try {
            return String.format(s, args);
        } catch (IllegalFormatException illegalformatexception) {
            return "Format error: " + s;
        }
    }
}
