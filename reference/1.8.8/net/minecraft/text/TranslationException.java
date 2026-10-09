package net.minecraft.text;

public class TranslationException extends IllegalArgumentException {
    public TranslationException(TranslatableText text, String reason) {
        super(String.format("Error parsing: %s: %s", text, reason));
    }

    public TranslationException(TranslatableText text, int index) {
        super(String.format("Invalid index %d requested for %s", index, text));
    }

    public TranslationException(TranslatableText text, Throwable exception) {
        super(String.format("Error while parsing: %s", text), exception);
    }
}
