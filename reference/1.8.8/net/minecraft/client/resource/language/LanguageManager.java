package net.minecraft.client.resource.language;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.client.resource.metadata.LanguageMetadata;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializerRegistry;
import net.minecraft.client.resource.pack.ResourcePack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LanguageManager implements ResourceReloadListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ResourceMetadataSerializerRegistry metadataSerializers;
    private String currentCode;
    protected static final Locale LOCALE = new Locale();
    private Map<String, Language> languages = Maps.newHashMap();

    public LanguageManager(ResourceMetadataSerializerRegistry metadataSerializers, String currentLanguage) {
        this.metadataSerializers = metadataSerializers;
        this.currentCode = currentLanguage;
        I18n.setLocale(LOCALE);
    }

    public void reload(List<ResourcePack> resourcePacks) {
        this.languages.clear();

        for (ResourcePack resourcepack : resourcePacks) {
            try {
                LanguageMetadata languagemetadata = resourcepack.getMetadataSection(this.metadataSerializers, "language");
                if (languagemetadata != null) {
                    for (Language language : languagemetadata.getLanguages()) {
                        if (!this.languages.containsKey(language.getCode())) {
                            this.languages.put(language.getCode(), language);
                        }
                    }
                }
            } catch (RuntimeException runtimeexception) {
                LOGGER.warn("Unable to parse metadata section of resourcepack: " + resourcepack.getName(), runtimeexception);
            } catch (IOException ioexception) {
                LOGGER.warn("Unable to parse metadata section of resourcepack: " + resourcepack.getName(), ioexception);
            }
        }
    }

    @Override
    public void reload(ResourceManager resourceManager) {
        List<String> list = Lists.newArrayList("en_US");
        if (!"en_US".equals(this.currentCode)) {
            list.add(this.currentCode);
        }

        LOCALE.load(resourceManager, list);
        net.minecraft.locale.Language.setTranslations(LOCALE.translations);
    }

    public boolean isUnicode() {
        return LOCALE.isUnicode();
    }

    public boolean isBidirectional() {
        return this.getLanguage() != null && this.getLanguage().isBidirectional();
    }

    public void setLanguage(Language language) {
        this.currentCode = language.getCode();
    }

    public Language getLanguage() {
        return this.languages.containsKey(this.currentCode) ? this.languages.get(this.currentCode) : this.languages.get("en_US");
    }

    public SortedSet<Language> getLanguages() {
        return Sets.newTreeSet(this.languages.values());
    }
}
