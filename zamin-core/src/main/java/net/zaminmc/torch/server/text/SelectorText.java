package net.zaminmc.torch.server.text;

/**
 * The 1.8.8 selector component (SelectorText.java 1:1): the pattern is
 * resolved against the command source by TextUtils.updateForEntity (the
 * reference's TargetSelector.getSelectionAsText walk).
 */
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
        if (!(object instanceof SelectorText selectortext)) {
            return false;
        }
        return this.pattern.equals(selectortext.pattern) && super.equals(object);
    }

    @Override
    public String toString() {
        return "SelectorComponent{pattern='" + this.pattern + "', siblings=" + this.siblings + ", style=" + this.getStyle() + '}';
    }
}
