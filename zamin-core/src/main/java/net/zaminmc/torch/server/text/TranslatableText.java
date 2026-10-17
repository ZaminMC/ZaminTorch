package net.zaminmc.torch.server.text;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * The 1.8.8 translatable component (TranslatableText.java 1:1, the
 * server-side half): the key, the with-args (each a raw Object — a String
 * or a nested component), and the arg-parenting style walk.
 *
 * <p>Known engine adaptation (ledgered): the reference's translations list
 * and {@code updateTranslations} machinery ride the client locale (I18n);
 * the engine carries no i18n layer, so the content walk answers the key
 * itself — exactly the reference's empty-locale fallback. The wire path
 * never reads the content (the serializer emits translate/with verbatim).</p>
 */
public class TranslatableText extends BaseText {
    private final String key;
    private final Object[] args;

    public TranslatableText(String key, Object... args) {
        this.key = key;
        this.args = args;
        for (Object object : args) {
            if (object instanceof Text text) {
                text.getStyle().setParent(this.getStyle());
            }
        }
    }

    public String getKey() {
        return this.key;
    }

    public Object[] getArgs() {
        return this.args;
    }

    @Override
    public String getContent() {
        // The reference consults the client locale (I18n); the engine's
        // no-locale fallback answers the key itself.
        return this.key;
    }

    @Override
    public Iterator<Text> iterator() {
        // The reference includes the translations here; without a locale the
        // translations list is empty, so the walk is the siblings alone.
        return BaseText.concatenate(this.siblings);
    }

    @Override
    public Text setStyle(Style style) {
        super.setStyle(style);
        for (Object object : this.args) {
            if (object instanceof Text text) {
                text.getStyle().setParent(this.getStyle());
            }
        }
        return this;
    }

    public TranslatableText copy() {
        Object[] aobject = new Object[this.args.length];
        for (int i = 0; i < this.args.length; i++) {
            if (this.args[i] instanceof Text text) {
                aobject[i] = text.copy();
            } else {
                aobject[i] = this.args[i];
            }
        }
        TranslatableText translatabletext = new TranslatableText(this.key, aobject);
        translatabletext.setStyle(this.getStyle().deepCopy());
        for (Text text : this.getSiblings()) {
            translatabletext.append(text.copy());
        }
        return translatabletext;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TranslatableText translatabletext)) {
            return false;
        }
        return java.util.Arrays.equals(this.args, translatabletext.args)
                && this.key.equals(translatabletext.key)
                && super.equals(object);
    }

    @Override
    public String toString() {
        return "TranslatableComponent{key='" + this.key + "', siblings=" + this.siblings + ", style=" + this.getStyle() + '}';
    }

    /** The reference's ARG_FORMAT (kept for the class shape's parity). */
    public static final java.util.regex.Pattern ARG_FORMAT =
            java.util.regex.Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
}
