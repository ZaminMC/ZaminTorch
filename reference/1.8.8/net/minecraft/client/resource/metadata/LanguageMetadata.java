package net.minecraft.client.resource.metadata;

import java.util.Collection;
import net.minecraft.client.resource.language.Language;

public class LanguageMetadata implements ResourceMetadataSection {
    private final Collection<Language> languages;

    public LanguageMetadata(Collection<Language> languages) {
        this.languages = languages;
    }

    public Collection<Language> getLanguages() {
        return this.languages;
    }
}
