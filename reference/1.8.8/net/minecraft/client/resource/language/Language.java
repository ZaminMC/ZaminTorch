package net.minecraft.client.resource.language;

public class Language implements Comparable<Language> {
    private final String code;
    private final String name;
    private final String region;
    private final boolean bidirectional;

    public Language(String code, String name, String region, boolean bidirectional) {
        this.code = code;
        this.name = name;
        this.region = region;
        this.bidirectional = bidirectional;
    }

    public String getCode() {
        return this.code;
    }

    public boolean isBidirectional() {
        return this.bidirectional;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", this.region, this.name);
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof Language && this.code.equals(((Language)object).code);
    }

    @Override
    public int hashCode() {
        return this.code.hashCode();
    }

    public int compareTo(Language language) {
        return this.code.compareTo(language.code);
    }
}
