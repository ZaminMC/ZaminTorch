package net.minecraft.text;

public class LiteralText extends BaseText {
    private final String string;

    public LiteralText(String string) {
        this.string = string;
    }

    public String getRawString() {
        return this.string;
    }

    @Override
    public String getContent() {
        return this.string;
    }

    public LiteralText copy() {
        LiteralText literaltext = new LiteralText(this.string);
        literaltext.setStyle(this.getStyle().deepCopy());

        for (Text text : this.getSiblings()) {
            literaltext.append(text.copy());
        }

        return literaltext;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof LiteralText)) {
            return false;
        }

        LiteralText literaltext = (LiteralText)object;
        return this.string.equals(literaltext.getRawString()) && super.equals(object);
    }

    @Override
    public String toString() {
        return "TextComponent{text='" + this.string + '\'' + ", siblings=" + this.siblings + ", style=" + this.getStyle() + '}';
    }
}
