package net.zaminmc.torch.server.text;

import net.zaminmc.torch.server.text.Json.Arr;
import net.zaminmc.torch.server.text.Json.Bool;
import net.zaminmc.torch.server.text.Json.NullV;
import net.zaminmc.torch.server.text.Json.Num;
import net.zaminmc.torch.server.text.Json.Obj;
import net.zaminmc.torch.server.text.Json.Str;
import net.zaminmc.torch.server.text.Json.Value;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The 1.8.8 component JSON serializer (Text.Serializer + Style.Serializer
 * 1:1) over the engine's dependency-free JSON walk — the exact GSON tree
 * semantics: the bare-literal short form, the style fields inlined into the
 * component's own object, extra as the sibling array, and the reference's
 * own dispatch order (text → translate+with → score → selector).
 *
 * <p>Known 1:1 quirks kept: an empty JSON array deserializes to null (the
 * reference's loop never assigns), a with-arg that is a bare literal
 * flattens to its raw string, the score's serialized "value" rides the live
 * board's getContent(), and toJson(null) answers the four-character "null"
 * exactly like GSON's own.</p>
 */
public final class TextJson {

    private TextJson() {
    }

    /**
     * The LowercaseEnumTypeAdapterFactory's map for Formatting: the exact
     * lowercase enum name ("dark_blue" — the condensed "darkblue" does NOT
     * resolve here, unlike Formatting.byName).
     */
    private static final Map<String, Formatting> FORMATTING_BY_JSON_KEY = new HashMap<>();

    static {
        for (Formatting formatting : Formatting.values()) {
            FORMATTING_BY_JSON_KEY.put(formatting.name().toLowerCase(Locale.US), formatting);
        }
    }

    // ------------------------------------------------------------------ read

    /** Text.Serializer.fromJson: null JSON answers null (GSON's own). */
    public static Text fromJson(String s) {
        Value tree = Json.parse(s);
        return deserialize(tree);
    }

    /** Text.Serializer.deserialize 1:1 over the engine's tree. */
    public static Text deserialize(Value element) {
        if (element instanceof Str || element instanceof Num || element instanceof Bool) {
            return new LiteralText(asString(element));
        }
        if (!(element instanceof Obj)) {
            if (element instanceof Arr jsonarray1) {
                Text text1 = null;
                for (int i = 0; i < jsonarray1.size(); i++) {
                    Text text2 = deserialize(jsonarray1.get(i));
                    if (text1 == null) {
                        text1 = text2;
                    } else {
                        text1.append(text2);
                    }
                }
                return text1;
            }
            throw new Json.JsonFormatException(
                    "Don't know how to turn " + Json.write(element) + " into a Component");
        }
        Obj jsonobject = (Obj) element;
        Text text;
        if (jsonobject.has("text")) {
            text = new LiteralText(asString(jsonobject.get("text")));
        } else if (jsonobject.has("translate")) {
            String s = asString(jsonobject.get("translate"));
            if (jsonobject.has("with")) {
                Arr jsonarray = jsonobject.getArr("with");
                Object[] aobject = new Object[jsonarray.size()];
                for (int i = 0; i < aobject.length; i++) {
                    aobject[i] = deserialize(jsonarray.get(i));
                    if (aobject[i] instanceof LiteralText literaltext) {
                        if (literaltext.getStyle().isEmpty() && literaltext.getSiblings().isEmpty()) {
                            aobject[i] = literaltext.getRawString();
                        }
                    }
                }
                text = new TranslatableText(s, aobject);
            } else {
                text = new TranslatableText(s);
            }
        } else if (jsonobject.has("score")) {
            Obj jsonobject1 = jsonobject.getObj("score");
            if (jsonobject1 == null || !jsonobject1.has("name") || !jsonobject1.has("objective")) {
                throw new Json.JsonFormatException("A score component needs a least a name and an objective");
            }
            text = new ScoreText(asString(jsonobject1.get("name")), asString(jsonobject1.get("objective")));
            if (jsonobject1.has("value")) {
                ((ScoreText) text).setValue(asString(jsonobject1.get("value")));
            }
        } else {
            if (!jsonobject.has("selector")) {
                throw new Json.JsonFormatException(
                        "Don't know how to turn " + Json.write(element) + " into a Component");
            }
            text = new SelectorText(asString(jsonobject.get("selector")));
        }

        if (jsonobject.has("extra")) {
            Arr jsonarray2 = jsonobject.getArr("extra");
            if (jsonarray2 == null || jsonarray2.size() <= 0) {
                throw new Json.JsonFormatException("Unexpected empty array of components");
            }
            for (int j = 0; j < jsonarray2.size(); j++) {
                text.append(deserialize(jsonarray2.get(j)));
            }
        }

        text.setStyle(deserializeStyle(jsonobject));
        return text;
    }

    /** Style.Serializer.deserialize 1:1 (the component's own object doubles as the style source). */
    static Style deserializeStyle(Obj jsonobject) {
        if (jsonobject == null) {
            return null;
        }
        Style style = new Style();
        if (jsonobject.has("bold")) {
            style.bold = asBoolean(jsonobject.get("bold"));
        }
        if (jsonobject.has("italic")) {
            style.italic = asBoolean(jsonobject.get("italic"));
        }
        if (jsonobject.has("underlined")) {
            style.underlined = asBoolean(jsonobject.get("underlined"));
        }
        if (jsonobject.has("strikethrough")) {
            style.strikethrough = asBoolean(jsonobject.get("strikethrough"));
        }
        if (jsonobject.has("obfuscated")) {
            style.obfuscated = asBoolean(jsonobject.get("obfuscated"));
        }
        if (jsonobject.has("color")) {
            style.color = FORMATTING_BY_JSON_KEY.get(asString(jsonobject.get("color")));
        }
        if (jsonobject.has("insertion")) {
            style.insertion = asString(jsonobject.get("insertion"));
        }
        if (jsonobject.has("clickEvent")) {
            Obj jsonobject1 = jsonobject.getObj("clickEvent");
            if (jsonobject1 != null) {
                Value jsonprimitive = jsonobject1.getPrim("action");
                ClickEvent.Action clickevent$action =
                        jsonprimitive == null ? null : ClickEvent.Action.byKey(asString(jsonprimitive));
                Value jsonprimitive1 = jsonobject1.getPrim("value");
                String s = jsonprimitive1 == null ? null : asString(jsonprimitive1);
                if (clickevent$action != null && s != null && clickevent$action.allowFromRemoteSource()) {
                    style.clickEvent = new ClickEvent(clickevent$action, s);
                }
            }
        }
        if (jsonobject.has("hoverEvent")) {
            Obj jsonobject2 = jsonobject.getObj("hoverEvent");
            if (jsonobject2 != null) {
                Value jsonprimitive2 = jsonobject2.getPrim("action");
                HoverEvent.Action hoverevent$action =
                        jsonprimitive2 == null ? null : HoverEvent.Action.byKey(asString(jsonprimitive2));
                Text text = jsonobject2.has("value") ? deserialize(jsonobject2.get("value")) : null;
                if (hoverevent$action != null && text != null && hoverevent$action.allowFromRemoteSource()) {
                    style.hoverEvent = new HoverEvent(hoverevent$action, text);
                }
            }
        }
        return style;
    }

    // ------------------------------------------------------------------ write

    /** Text.Serializer.toJson: GSON's own null form. */
    public static String toJson(Text text) {
        return text == null ? "null" : Json.write(serialize(text));
    }

    /** Text.Serializer.serialize 1:1. */
    public static Value serialize(Text text) {
        if (text instanceof LiteralText literaltext
                && text.getStyle().isEmpty() && text.getSiblings().isEmpty()) {
            return new Str(literaltext.getRawString());
        }

        Obj jsonobject = Obj.obj();
        if (!text.getStyle().isEmpty()) {
            addStyle(text.getStyle(), jsonobject);
        }

        if (!text.getSiblings().isEmpty()) {
            Arr jsonarray = new Arr();
            for (Text textx : text.getSiblings()) {
                jsonarray.add(serialize(textx));
            }
            jsonobject.add("extra", jsonarray);
        }

        if (text instanceof LiteralText lit) {
            jsonobject.addString("text", lit.getRawString());
        } else if (text instanceof TranslatableText translatabletext) {
            jsonobject.addString("translate", translatabletext.getKey());
            if (translatabletext.getArgs() != null && translatabletext.getArgs().length > 0) {
                Arr jsonarray1 = new Arr();
                for (Object object : translatabletext.getArgs()) {
                    if (object instanceof Text textArg) {
                        jsonarray1.add(serialize(textArg));
                    } else {
                        jsonarray1.add(new Str(String.valueOf(object)));
                    }
                }
                jsonobject.add("with", jsonarray1);
            }
        } else if (text instanceof ScoreText scoretext) {
            Obj jsonobject1 = Obj.obj();
            jsonobject1.addString("name", scoretext.getOwner());
            jsonobject1.addString("objective", scoretext.getObjective());
            jsonobject1.addString("value", scoretext.getContent());
            jsonobject.add("score", jsonobject1);
        } else {
            if (!(text instanceof SelectorText selectortext)) {
                throw new IllegalArgumentException("Don't know how to serialize " + text + " as a Component");
            }
            jsonobject.addString("selector", selectortext.getPattern());
        }

        return jsonobject;
    }

    /** The reference's addStyle: the style's fields merged into the component's object. */
    private static void addStyle(Style style, Obj json) {
        Obj styleJson = serializeStyle(style);
        if (styleJson != null) {
            for (Json.Member entry : styleJson.members()) {
                json.add(entry.key(), entry.value());
            }
        }
    }

    /** Style.Serializer.serialize 1:1 — null when empty (the callers guard). */
    static Obj serializeStyle(Style style) {
        if (style.isEmpty()) {
            return null;
        }
        Obj jsonobject = Obj.obj();
        if (style.bold != null) {
            jsonobject.addBool("bold", style.bold);
        }
        if (style.italic != null) {
            jsonobject.addBool("italic", style.italic);
        }
        if (style.underlined != null) {
            jsonobject.addBool("underlined", style.underlined);
        }
        if (style.strikethrough != null) {
            jsonobject.addBool("strikethrough", style.strikethrough);
        }
        if (style.obfuscated != null) {
            jsonobject.addBool("obfuscated", style.obfuscated);
        }
        if (style.color != null) {
            jsonobject.addString("color", style.color.name().toLowerCase(Locale.US));
        }
        if (style.insertion != null) {
            jsonobject.addString("insertion", style.insertion);
        }
        if (style.clickEvent != null) {
            Obj jsonobject1 = Obj.obj();
            jsonobject1.addString("action", style.clickEvent.getAction().getKey());
            jsonobject1.addString("value", style.clickEvent.getValue());
            jsonobject.add("clickEvent", jsonobject1);
        }
        if (style.hoverEvent != null) {
            Obj jsonobject2 = Obj.obj();
            jsonobject2.addString("action", style.hoverEvent.getAction().getKey());
            jsonobject2.add("value", serialize(style.hoverEvent.getValue()));
            jsonobject.add("hoverEvent", jsonobject2);
        }
        return jsonobject;
    }

    // ------------------------------------------------------------------ casts

    /** GSON's getAsString over a primitive. */
    private static String asString(Value v) {
        if (v instanceof Str s) {
            return s.getAsString();
        }
        if (v instanceof Num n) {
            return n.getAsString();
        }
        if (v instanceof Bool b) {
            return b.value() ? "true" : "false";
        }
        throw new Json.JsonFormatException("Expected a string primitive but was " + typeName(v));
    }

    private static String primAsString(Value v) {
        return asString(v);
    }

    /** GSON's getAsBoolean over a primitive. */
    private static boolean asBoolean(Value v) {
        if (v instanceof Bool b) {
            return b.getAsBoolean();
        }
        throw new Json.JsonFormatException("Expected a boolean primitive but was " + typeName(v));
    }

    private static String typeName(Value v) {
        if (v == null || v instanceof NullV) {
            return "null";
        }
        return v.getClass().getSimpleName();
    }
}
