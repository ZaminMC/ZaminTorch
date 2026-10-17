package net.zaminmc.torch.server.text;

import net.zaminmc.torch.server.command.TargetSelector;
import net.zaminmc.torch.server.player.PlayerSession;

import java.util.List;

/**
 * The 1.8.8 text resolver (TextUtils.java 1:1): updateForEntity resolves a
 * component against the command source before the wire — the score owners
 * through the selector (exactly one match), the selector components into the
 * matched display names (the "a, b and c" listText join), and the translate
 * args recursively. The reference's EntityNotFound surfaces as the engine's
 * IllegalArgumentException convention.
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * The reference's updateForEntity(CommandSource, Text, Entity): the
     * resolved copy (the source text is never mutated).
     */
    public static Text updateForEntity(TargetSelector.SourceView source, Text text, PlayerSession entity) {
        Text textx = null;
        if (text instanceof ScoreText scoretext) {
            String s = scoretext.getOwner();
            if (TargetSelector.isValid(s)) {
                List<TargetSelector.Candidate> list = TargetSelector.select(source, s);
                if (list.size() != 1) {
                    throw new IllegalArgumentException("That entity could not be found");
                }
                s = list.get(0).name();
            }

            textx = entity != null && s.equals("*")
                    ? new ScoreText(entity.name(), scoretext.getObjective())
                    : new ScoreText(s, scoretext.getObjective());
            ((ScoreText) textx).setValue(scoretext.getContent());
        } else if (text instanceof SelectorText) {
            String s1 = ((SelectorText) text).getPattern();
            textx = getSelectionAsText(source, s1);
            if (textx == null) {
                textx = new LiteralText("");
            }
        } else if (text instanceof LiteralText) {
            textx = new LiteralText(((LiteralText) text).getRawString());
        } else {
            if (!(text instanceof TranslatableText)) {
                return text;
            }

            Object[] aobject = ((TranslatableText) text).getArgs();
            for (int i = 0; i < aobject.length; i++) {
                Object object = aobject[i];
                if (object instanceof Text) {
                    aobject[i] = updateForEntity(source, (Text) object, entity);
                }
            }

            textx = new TranslatableText(((TranslatableText) text).getKey(), aobject);
        }

        Style style = text.getStyle();
        if (style != null) {
            textx.setStyle(style.deepCopy());
        }

        for (Text text1 : text.getSiblings()) {
            textx.append(updateForEntity(source, text1, entity));
        }

        return textx;
    }

    /**
     * The reference's TargetSelector.getSelectionAsText + AbstractCommand
     * .listText: the matched display names joined by the empty head + the
     * ", " / " and " walk.
     */
    public static Text getSelectionAsText(TargetSelector.SourceView source, String pattern) {
        List<TargetSelector.Candidate> list = TargetSelector.select(source, pattern);
        if (list.isEmpty()) {
            return null;
        }
        List<Text> names = new java.util.ArrayList<>();
        for (TargetSelector.Candidate candidate : list) {
            names.add(new LiteralText(candidate.name()));
        }
        return listText(names);
    }

    /** The reference's AbstractCommand.listText: the "a, b and c" join. */
    public static Text listText(List<Text> text) {
        Text textx = new LiteralText("");
        for (int i = 0; i < text.size(); i++) {
            if (i > 0) {
                if (i == text.size() - 1) {
                    textx.append(" and ");
                } else {
                    textx.append(", ");
                }
            }
            textx.append(text.get(i));
        }
        return textx;
    }
}
