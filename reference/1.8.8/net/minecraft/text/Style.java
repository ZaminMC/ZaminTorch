package net.minecraft.text;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class Style {
    private Style parent;
    private Formatting color;
    private Boolean bold;
    private Boolean italic;
    private Boolean underlined;
    private Boolean strikethrough;
    private Boolean obfuscated;
    private ClickEvent clickEvent;
    private HoverEvent hoverEvent;
    private String insertion;
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

    public String asString() {
        if (this.isEmpty()) {
            return this.parent != null ? this.parent.asString() : "";
        }

        StringBuilder stringbuilder = new StringBuilder();
        if (this.getColor() != null) {
            stringbuilder.append(this.getColor());
        }

        if (this.isBold()) {
            stringbuilder.append(Formatting.BOLD);
        }

        if (this.isItalic()) {
            stringbuilder.append(Formatting.ITALIC);
        }

        if (this.isUnderlined()) {
            stringbuilder.append(Formatting.UNDERLINE);
        }

        if (this.isObfuscated()) {
            stringbuilder.append(Formatting.OBFUSCATED);
        }

        if (this.isStrikethrough()) {
            stringbuilder.append(Formatting.STRIKETHROUGH);
        }

        return stringbuilder.toString();
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

        if (!(object instanceof Style)) {
            return false;
        }

        Style style = (Style)object;
        return this.isBold() == style.isBold()
            && this.getColor() == style.getColor()
            && this.isItalic() == style.isItalic()
            && this.isObfuscated() == style.isObfuscated()
            && this.isStrikethrough() == style.isStrikethrough()
            && this.isUnderlined() == style.isUnderlined()
            && (this.getClickEvent() != null ? this.getClickEvent().equals(style.getClickEvent()) : style.getClickEvent() == null)
            && (this.getHoverEvent() != null ? this.getHoverEvent().equals(style.getHoverEvent()) : style.getHoverEvent() == null)
            && (this.getInsertion() != null ? this.getInsertion().equals(style.getInsertion()) : style.getInsertion() == null);
    }

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

    public static class Serializer implements JsonDeserializer<Style>, JsonSerializer<Style> {
        public Style deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            if (jsonElement.isJsonObject()) {
                Style style = new Style();
                JsonObject jsonobject = jsonElement.getAsJsonObject();
                if (jsonobject == null) {
                    return null;
                }

                if (jsonobject.has("bold")) {
                    style.bold = jsonobject.get("bold").getAsBoolean();
                }

                if (jsonobject.has("italic")) {
                    style.italic = jsonobject.get("italic").getAsBoolean();
                }

                if (jsonobject.has("underlined")) {
                    style.underlined = jsonobject.get("underlined").getAsBoolean();
                }

                if (jsonobject.has("strikethrough")) {
                    style.strikethrough = jsonobject.get("strikethrough").getAsBoolean();
                }

                if (jsonobject.has("obfuscated")) {
                    style.obfuscated = jsonobject.get("obfuscated").getAsBoolean();
                }

                if (jsonobject.has("color")) {
                    style.color = jsonDeserializationContext.deserialize(jsonobject.get("color"), Formatting.class);
                }

                if (jsonobject.has("insertion")) {
                    style.insertion = jsonobject.get("insertion").getAsString();
                }

                if (jsonobject.has("clickEvent")) {
                    JsonObject jsonobject1 = jsonobject.getAsJsonObject("clickEvent");
                    if (jsonobject1 != null) {
                        JsonPrimitive jsonprimitive = jsonobject1.getAsJsonPrimitive("action");
                        ClickEvent.Action clickevent$action = jsonprimitive == null ? null : ClickEvent.Action.byKey(jsonprimitive.getAsString());
                        JsonPrimitive jsonprimitive1 = jsonobject1.getAsJsonPrimitive("value");
                        String s = jsonprimitive1 == null ? null : jsonprimitive1.getAsString();
                        if (clickevent$action != null && s != null && clickevent$action.allowFromRemoteSource()) {
                            style.clickEvent = new ClickEvent(clickevent$action, s);
                        }
                    }
                }

                if (jsonobject.has("hoverEvent")) {
                    JsonObject jsonobject2 = jsonobject.getAsJsonObject("hoverEvent");
                    if (jsonobject2 != null) {
                        JsonPrimitive jsonprimitive2 = jsonobject2.getAsJsonPrimitive("action");
                        HoverEvent.Action hoverevent$action = jsonprimitive2 == null ? null : HoverEvent.Action.byKey(jsonprimitive2.getAsString());
                        Text text = jsonDeserializationContext.deserialize(jsonobject2.get("value"), Text.class);
                        if (hoverevent$action != null && text != null && hoverevent$action.allowFromRemoteSource()) {
                            style.hoverEvent = new HoverEvent(hoverevent$action, text);
                        }
                    }
                }

                return style;
            } else {
                return null;
            }
        }

        public JsonElement serialize(Style style, Type type, JsonSerializationContext jsonSerializationContext) {
            if (style.isEmpty()) {
                return null;
            }

            JsonObject jsonobject = new JsonObject();
            if (style.bold != null) {
                jsonobject.addProperty("bold", style.bold);
            }

            if (style.italic != null) {
                jsonobject.addProperty("italic", style.italic);
            }

            if (style.underlined != null) {
                jsonobject.addProperty("underlined", style.underlined);
            }

            if (style.strikethrough != null) {
                jsonobject.addProperty("strikethrough", style.strikethrough);
            }

            if (style.obfuscated != null) {
                jsonobject.addProperty("obfuscated", style.obfuscated);
            }

            if (style.color != null) {
                jsonobject.add("color", jsonSerializationContext.serialize(style.color));
            }

            if (style.insertion != null) {
                jsonobject.add("insertion", jsonSerializationContext.serialize(style.insertion));
            }

            if (style.clickEvent != null) {
                JsonObject jsonobject1 = new JsonObject();
                jsonobject1.addProperty("action", style.clickEvent.getAction().getKey());
                jsonobject1.addProperty("value", style.clickEvent.getValue());
                jsonobject.add("clickEvent", jsonobject1);
            }

            if (style.hoverEvent != null) {
                JsonObject jsonobject2 = new JsonObject();
                jsonobject2.addProperty("action", style.hoverEvent.getAction().getKey());
                jsonobject2.add("value", jsonSerializationContext.serialize(style.hoverEvent.getValue()));
                jsonobject.add("hoverEvent", jsonobject2);
            }

            return jsonobject;
        }
    }
}
