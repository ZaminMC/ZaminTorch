package net.minecraft.text;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.locale.I18n;

public class TranslatableText extends BaseText {
    private final String key;
    private final Object[] args;
    private final Object lock = new Object();
    private long languageReloadTimestamp = -1L;
    List<Text> translations = Lists.newArrayList();
    public static final Pattern ARG_FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    public TranslatableText(String key, Object... args) {
        this.key = key;
        this.args = args;

        for (Object object : args) {
            if (object instanceof Text) {
                ((Text)object).getStyle().setParent(this.getStyle());
            }
        }
    }

    synchronized void updateTranslations() {
        synchronized (this.lock) {
            long i = I18n.getLoadTime();
            if (i == this.languageReloadTimestamp) {
                return;
            }

            this.languageReloadTimestamp = i;
            this.translations.clear();
        }

        try {
            this.setTranslation(I18n.translate(this.key));
        } catch (TranslationException translationexception1) {
            this.translations.clear();

            try {
                this.setTranslation(I18n.translateDefault(this.key));
            } catch (TranslationException translationexception) {
                throw translationexception1;
            }
        }
    }

    protected void setTranslation(String translation) {
        boolean flag = false;
        Matcher matcher = ARG_FORMAT.matcher(translation);
        int i = 0;
        int j = 0;

        try {
            while (matcher.find(j)) {
                int k = matcher.start();
                int l = matcher.end();
                if (k > j) {
                    LiteralText literaltext = new LiteralText(String.format(translation.substring(j, k)));
                    literaltext.getStyle().setParent(this.getStyle());
                    this.translations.add(literaltext);
                }

                String s2 = matcher.group(2);
                String s = translation.substring(k, l);
                if ("%".equals(s2) && "%%".equals(s)) {
                    LiteralText literaltext2 = new LiteralText("%");
                    literaltext2.getStyle().setParent(this.getStyle());
                    this.translations.add(literaltext2);
                } else {
                    if (!"s".equals(s2)) {
                        throw new TranslationException(this, "Unsupported format: '" + s + "'");
                    }

                    String s1 = matcher.group(1);
                    int i1 = s1 != null ? Integer.parseInt(s1) - 1 : i++;
                    if (i1 < this.args.length) {
                        this.translations.add(this.getArg(i1));
                    }
                }

                j = l;
            }

            if (j < translation.length()) {
                LiteralText literaltext1 = new LiteralText(String.format(translation.substring(j)));
                literaltext1.getStyle().setParent(this.getStyle());
                this.translations.add(literaltext1);
            }
        } catch (IllegalFormatException illegalformatexception) {
            throw new TranslationException(this, illegalformatexception);
        }
    }

    private Text getArg(int index) {
        if (index >= this.args.length) {
            throw new TranslationException(this, index);
        }

        Object object = this.args[index];
        Text text;
        if (object instanceof Text) {
            text = (Text)object;
        } else {
            text = new LiteralText(object == null ? "null" : object.toString());
            text.getStyle().setParent(this.getStyle());
        }

        return text;
    }

    @Override
    public Text setStyle(Style style) {
        super.setStyle(style);

        for (Object object : this.args) {
            if (object instanceof Text) {
                ((Text)object).getStyle().setParent(this.getStyle());
            }
        }

        if (this.languageReloadTimestamp > -1L) {
            for (Text text : this.translations) {
                text.getStyle().setParent(style);
            }
        }

        return this;
    }

    @Override
    public Iterator<Text> iterator() {
        this.updateTranslations();
        return Iterators.concat(concatenate(this.translations), concatenate(this.siblings));
    }

    @Override
    public String getContent() {
        this.updateTranslations();
        StringBuilder stringbuilder = new StringBuilder();

        for (Text text : this.translations) {
            stringbuilder.append(text.getContent());
        }

        return stringbuilder.toString();
    }

    public TranslatableText copy() {
        Object[] aobject = new Object[this.args.length];

        for (int i = 0; i < this.args.length; i++) {
            if (this.args[i] instanceof Text) {
                aobject[i] = ((Text)this.args[i]).copy();
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

        if (!(object instanceof TranslatableText)) {
            return false;
        }

        TranslatableText translatabletext = (TranslatableText)object;
        return Arrays.equals(this.args, translatabletext.args) && this.key.equals(translatabletext.key) && super.equals(object);
    }

    @Override
    public int hashCode() {
        int i = super.hashCode();
        i = 31 * i + this.key.hashCode();
        return 31 * i + Arrays.hashCode(this.args);
    }

    @Override
    public String toString() {
        return "TranslatableComponent{key='"
            + this.key
            + '\''
            + ", args="
            + Arrays.toString(this.args)
            + ", siblings="
            + this.siblings
            + ", style="
            + this.getStyle()
            + '}';
    }

    public String getKey() {
        return this.key;
    }

    public Object[] getArgs() {
        return this.args;
    }
}
