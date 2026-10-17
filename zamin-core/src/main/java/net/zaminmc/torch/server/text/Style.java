package net.zaminmc.torch.server.text;

/**
 * The 1.8.8 component style (Style.java 1:1): the parent-chained reads (a
 * null field falls to the parent, the parentless to ROOT), the mutable
 * setters, and the reference's own quirks —
 *
 * <ul>
 *   <li>{@link #isEmpty()} omits {@code insertion} (a style carrying only an
 *       insertion serializes as empty — the reference's own field list);</li>
 *   <li>{@code hashCode()} dereferences every field unconditionally (the
 *       reference NPEs on a null color — kept verbatim; the engine's wire
 *       path never hashes a Style).</li>
 * </ul>
 */
public class Style {
    Style parent;
    Formatting color;
    Boolean bold;
    Boolean italic;
    Boolean underlined;
    Boolean strikethrough;
    Boolean obfuscated;
    ClickEvent clickEvent;
    HoverEvent hoverEvent;
    String insertion;

    /** The reference's frozen ROOT (the null-parent fallback): every read answers the empty default. */
    private static final Style ROOT = new Style() {
        @Override
        public Formatting getColor() {
            return null;
        }

        @Override
        public boolean isBold() {
            return false;
        }

        @Override
        public boolean isItalic() {
            return false;
        }

        @Override
        public boolean isStrikethrough() {
            return false;
        }

        @Override
        public boolean isUnderlined() {
            return false;
        }

        @Override
        public boolean isObfuscated() {
            return false;
        }

        @Override
        public ClickEvent getClickEvent() {
            return null;
        }

        @Override
        public HoverEvent getHoverEvent() {
            return null;
        }

        @Override
        public String getInsertion() {
            return null;
        }

        @Override
        public Style setColor(Formatting color) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Style setBold(Boolean bold) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Style setItalic(Boolean italic) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Style setStrikethrough(Boolean strikethrough) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Style setUnderlined(Boolean underlined) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Style setObfuscated(Boolean obfuscated) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Style setClickEvent(ClickEvent clickEvent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Style setHoverEvent(HoverEvent clickEvent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Style setParent(Style parent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "Style.ROOT";
        }

        @Override
        public Style deepCopy() {
            return this;
        }

        @Override
        public Style copy() {
            return this;
        }

        @Override
        public String asString() {
            return "";
        }
    };

    public Formatting getColor() {
        return this.color == null ? this.getParent().getColor() : this.color;
    }

    public boolean isBold() {
        return this.bold == null ? this.getParent().isBold() : this.bold;
    }

    public boolean isItalic() {
        return this.italic == null ? this.getParent().isItalic() : this.italic;
    }

    public boolean isStrikethrough() {
        return this.strikethrough == null ? this.getParent().isStrikethrough() : this.strikethrough;
    }

    public boolean isUnderlined() {
        return this.underlined == null ? this.getParent().isUnderlined() : this.underlined;
    }

    public boolean isObfuscated() {
        return this.obfuscated == null ? this.getParent().isObfuscated() : this.obfuscated;
    }

    /**
     * The reference's own field list: bold, italic, strikethrough, underlined,
     * obfuscated, color, clickEvent, hoverEvent — the insertion is NOT here
     * (an insertion-only style answers empty).
     */
    public boolean isEmpty() {
        return this.bold == null
                && this.italic == null
                && this.strikethrough == null
                && this.underlined == null
                && this.obfuscated == null
                && this.color == null
                && this.clickEvent == null
                && this.hoverEvent == null;
    }

    public ClickEvent getClickEvent() {
        return this.clickEvent == null ? this.getParent().getClickEvent() : this.clickEvent;
    }

    public HoverEvent getHoverEvent() {
        return this.hoverEvent == null ? this.getParent().getHoverEvent() : this.hoverEvent;
    }

    public String getInsertion() {
        return this.insertion == null ? this.getParent().getInsertion() : this.insertion;
    }

    public Style setColor(Formatting color) {
        this.color = color;
        return this;
    }

    public Style setBold(Boolean bold) {
        this.bold = bold;
        return this;
    }

    public Style setItalic(Boolean italic) {
        this.italic = italic;
        return this;
    }

    public Style setStrikethrough(Boolean strikethrough) {
        this.strikethrough = strikethrough;
        return this;
    }

    public Style setUnderlined(Boolean underlined) {
        this.underlined = underlined;
        return this;
    }

    public Style setObfuscated(Boolean obfuscated) {
        this.obfuscated = obfuscated;
        return this;
    }

    public Style setClickEvent(ClickEvent clickEvent) {
        this.clickEvent = clickEvent;
        return this;
    }

    public Style setHoverEvent(HoverEvent clickEvent) {
        this.hoverEvent = clickEvent;
        return this;
    }

    public Style setInsertion(String insertion) {
        this.insertion = insertion;
        return this;
    }

    public Style setParent(Style parent) {
        this.parent = parent;
        return this;
    }

    /** The §-code prefix walk (the color first, then the modifiers in the reference's own order). */
    public String asString() {
        if (this.isEmpty()) {
            return this.parent != null ? this.parent.asString() : "";
        }

        StringBuilder sb = new StringBuilder();
        if (this.getColor() != null) {
            sb.append(this.getColor());
        }
        if (this.isBold()) {
            sb.append(Formatting.BOLD);
        }
        if (this.isItalic()) {
            sb.append(Formatting.ITALIC);
        }
        if (this.isUnderlined()) {
            sb.append(Formatting.UNDERLINE);
        }
        if (this.isObfuscated()) {
            sb.append(Formatting.OBFUSCATED);
        }
        if (this.isStrikethrough()) {
            sb.append(Formatting.STRIKETHROUGH);
        }
        return sb.toString();
    }

    private Style getParent() {
        return this.parent == null ? ROOT : this.parent;
    }

    @Override
    public String toString() {
        return "Style{hasParent="
                + (this.parent != null)
                + ", color="
                + this.color
                + ", bold="
                + this.bold
                + ", italic="
                + this.italic
                + ", underlined="
                + this.underlined
                + ", obfuscated="
                + this.obfuscated
                + ", clickEvent="
                + this.getClickEvent()
                + ", hoverEvent="
                + this.getHoverEvent()
                + ", insertion="
                + this.getInsertion()
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Style style)) {
            return false;
        }
        return this.isBold() == style.isBold()
                && this.getColor() == style.getColor()
                && this.isItalic() == style.isItalic()
                && this.isObfuscated() == style.isObfuscated()
                && this.isStrikethrough() == style.isStrikethrough()
                && this.isUnderlined() == style.isUnderlined()
                && (this.getClickEvent() != null
                        ? this.getClickEvent().equals(style.getClickEvent())
                        : style.getClickEvent() == null)
                && (this.getHoverEvent() != null
                        ? this.getHoverEvent().equals(style.getHoverEvent())
                        : style.getHoverEvent() == null)
                && (this.getInsertion() != null
                        ? this.getInsertion().equals(style.getInsertion())
                        : style.getInsertion() == null);
    }

    /**
     * The reference's own hash walk — every field dereferenced
     * unconditionally (a null color NPEs there; kept verbatim).
     */
    @Override
    public int hashCode() {
        int i = this.color.hashCode();
        i = 31 * i + this.bold.hashCode();
        i = 31 * i + this.italic.hashCode();
        i = 31 * i + this.underlined.hashCode();
        i = 31 * i + this.strikethrough.hashCode();
        i = 31 * i + this.obfuscated.hashCode();
        i = 31 * i + this.clickEvent.hashCode();
        i = 31 * i + this.hoverEvent.hashCode();
        return 31 * i + this.insertion.hashCode();
    }

    public Style deepCopy() {
        Style style = new Style();
        style.bold = this.bold;
        style.italic = this.italic;
        style.strikethrough = this.strikethrough;
        style.underlined = this.underlined;
        style.obfuscated = this.obfuscated;
        style.color = this.color;
        style.clickEvent = this.clickEvent;
        style.hoverEvent = this.hoverEvent;
        style.parent = this.parent;
        style.insertion = this.insertion;
        return style;
    }

    public Style copy() {
        Style style = new Style();
        style.setBold(this.isBold());
        style.setItalic(this.isItalic());
        style.setStrikethrough(this.isStrikethrough());
        style.setUnderlined(this.isUnderlined());
        style.setObfuscated(this.isObfuscated());
        style.setColor(this.getColor());
        style.setClickEvent(this.getClickEvent());
        style.setHoverEvent(this.getHoverEvent());
        style.setInsertion(this.getInsertion());
        return style;
    }
}
