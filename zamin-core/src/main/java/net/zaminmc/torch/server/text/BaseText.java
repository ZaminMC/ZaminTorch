package net.zaminmc.torch.server.text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The 1.8.8 component base (BaseText.java 1:1): the sibling list, the lazy
 * style, the parent-chaining append/setStyle, and the reference's own
 * depth-first iteration walk (this component, then every sibling's own
 * flattened iterator — each yielded element a copy with a copied style).
 */
public abstract class BaseText implements Text {
    protected List<Text> siblings = new ArrayList<>();
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
        // The reference's Iterators.concat(forArray(this), concatenate(siblings)):
        // the head element is yielded RAW, the siblings' flattened walk yields
        // each element copied with a copied style.
        return new RawThenOther(this, concatenate(this.siblings));
    }

    /**
     * The reference's concatenate: the flattened iterator over the given
     * components, every element mapped to {@code copy() + style.copy()}.
     */
    public static Iterator<Text> concatenate(Iterable<Text> text) {
        List<Iterator<Text>> sources = new ArrayList<>();
        for (Text t : text) {
            sources.add(t.iterator());
        }
        return new CopyingIterator(new FlatIterator(sources.iterator()));
    }

    @Override
    public final String getString() {
        StringBuilder sb = new StringBuilder();
        for (Text text : this) {
            sb.append(text.getContent());
        }
        return sb.toString();
    }

    @Override
    public final String getFormattedString() {
        StringBuilder sb = new StringBuilder();
        for (Text text : this) {
            sb.append(text.getStyle().asString());
            sb.append(text.getContent());
            sb.append(Formatting.RESET);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof BaseText basetext)) {
            return false;
        }
        return this.siblings.equals(basetext.siblings) && this.getStyle().equals(basetext.getStyle());
    }

    /**
     * The reference's own hash walk (this.style dereferenced directly — a
     * never-fetched style is null there and NPEs; the engine keeps the walk
     * but guards the lazy fetch, matching the fetched-state behavior).
     */
    @Override
    public int hashCode() {
        this.getStyle();
        return 31 * this.style.hashCode() + this.siblings.hashCode();
    }

    @Override
    public String toString() {
        return "BaseComponent{style=" + this.style + ", siblings=" + this.siblings + '}';
    }

    // ------------------------------------------------------------------ walks

    /** Yields the head element raw, then every element of the second iterator. */
    private static final class RawThenOther implements Iterator<Text> {
        private final Text head;
        private final Iterator<Text> other;
        private boolean headDone;

        RawThenOther(Text head, Iterator<Text> other) {
            this.head = head;
            this.other = other;
        }

        @Override
        public boolean hasNext() {
            return !headDone || other.hasNext();
        }

        @Override
        public Text next() {
            if (!headDone) {
                headDone = true;
                return head;
            }
            return other.next();
        }
    }

    /** Iterators.concat: every source's elements, in order. */
    private static final class FlatIterator implements Iterator<Text> {
        private final Iterator<Iterator<Text>> sources;
        private Iterator<Text> current;

        FlatIterator(Iterator<Iterator<Text>> sources) {
            this.sources = sources;
        }

        @Override
        public boolean hasNext() {
            while (current == null || !current.hasNext()) {
                if (!sources.hasNext()) {
                    return false;
                }
                current = sources.next();
            }
            return true;
        }

        @Override
        public Text next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return current.next();
        }
    }

    /** The reference's transform: every yielded element copied + style-copied. */
    private static final class CopyingIterator implements Iterator<Text> {
        private final Iterator<Text> source;

        CopyingIterator(Iterator<Text> source) {
            this.source = source;
        }

        @Override
        public boolean hasNext() {
            return source.hasNext();
        }

        @Override
        public Text next() {
            Text text = source.next();
            Text copy = text.copy();
            copy.setStyle(copy.getStyle().copy());
            return copy;
        }
    }
}
