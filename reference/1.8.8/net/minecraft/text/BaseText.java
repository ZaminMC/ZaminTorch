package net.minecraft.text;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;

public abstract class BaseText implements Text {
    protected List<Text> siblings = Lists.newArrayList();
    private Style style;

    @Override
    public Text append(Text text) {
        text.getStyle().setParent(this.getStyle());
        this.siblings.add(text);
        return this;
    }

    @Override
    public List<Text> getSiblings() {
        return this.siblings;
    }

    @Override
    public Text append(String text) {
        return this.append(new LiteralText(text));
    }

    @Override
    public Text setStyle(Style style) {
        this.style = style;

        for (Text text : this.siblings) {
            text.getStyle().setParent(this.getStyle());
        }

        return this;
    }

    @Override
    public Style getStyle() {
        if (this.style == null) {
            this.style = new Style();

            for (Text text : this.siblings) {
                text.getStyle().setParent(this.style);
            }
        }

        return this.style;
    }

    @Override
    public Iterator<Text> iterator() {
        return Iterators.concat(Iterators.forArray(this), concatenate(this.siblings));
    }

    @Override
    public final String getString() {
        StringBuilder stringbuilder = new StringBuilder();

        for (Text text : this) {
            stringbuilder.append(text.getContent());
        }

        return stringbuilder.toString();
    }

    @Override
    public final String getFormattedString() {
        StringBuilder stringbuilder = new StringBuilder();

        for (Text text : this) {
            stringbuilder.append(text.getStyle().asString());
            stringbuilder.append(text.getContent());
            stringbuilder.append(Formatting.RESET);
        }

        return stringbuilder.toString();
    }

    public static Iterator<Text> concatenate(Iterable<Text> text) {
        Iterator<Text> iterator = Iterators.concat(Iterators.transform(text.iterator(), new Function<Text, Iterator<Text>>() {
            public Iterator<Text> apply(Text text) {
                return text.iterator();
            }
        }));
        return Iterators.transform(iterator, new Function<Text, Text>() {
            public Text apply(Text text) {
                Text textx = text.copy();
                textx.setStyle(textx.getStyle().copy());
                return textx;
            }
        });
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof BaseText)) {
            return false;
        }

        BaseText basetext = (BaseText)object;
        return this.siblings.equals(basetext.siblings) && this.getStyle().equals(basetext.getStyle());
    }

    @Override
    public int hashCode() {
        return 31 * this.style.hashCode() + this.siblings.hashCode();
    }

    @Override
    public String toString() {
        return "BaseComponent{style=" + this.style + ", siblings=" + this.siblings + '}';
    }
}
