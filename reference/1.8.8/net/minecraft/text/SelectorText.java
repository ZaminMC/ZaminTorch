package net.minecraft.text;

public class SelectorText extends BaseText {
    private final String pattern;

    public SelectorText(String pattern) {
        this.pattern = pattern;
    }

    public String getPattern() {
        return this.pattern;
    }

    @Override
    public String getContent() {
        return this.pattern;
    }

    public SelectorText copy() {
        SelectorText selectortext = new SelectorText(this.pattern);
        selectortext.setStyle(this.getStyle().deepCopy());

        for (Text text : this.getSiblings()) {
            selectortext.append(text.copy());
        }

        return selectortext;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof SelectorText)) {
            return false;
        }

        SelectorText selectortext = (SelectorText)object;
        return this.pattern.equals(selectortext.pattern) && super.equals(object);
    }

    @Override
    public String toString() {
        return "SelectorComponent{pattern='" + this.pattern + '\'' + ", siblings=" + this.siblings + ", style=" + this.getStyle() + '}';
    }
}
