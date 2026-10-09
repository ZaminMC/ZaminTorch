package net.minecraft.text;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.TargetSelector;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.EntityNotFoundException;
import net.minecraft.server.command.source.CommandSource;

public class TextUtils {
    public static Text updateForEntity(CommandSource source, Text text, Entity entity) throws CommandException {
        Text textx = null;
        if (text instanceof ScoreText) {
            ScoreText scoretext = (ScoreText)text;
            String s = scoretext.getOwner();
            if (TargetSelector.isValid(s)) {
                List<Entity> list = TargetSelector.select(source, s, Entity.class);
                if (list.size() != 1) {
                    throw new EntityNotFoundException();
                }

                s = list.get(0).getName();
            }

            textx = entity != null && s.equals("*") ? new ScoreText(entity.getName(), scoretext.getObjective()) : new ScoreText(s, scoretext.getObjective());
            ((ScoreText)textx).setValue(scoretext.getContent());
        } else if (text instanceof SelectorText) {
            String s1 = ((SelectorText)text).getPattern();
            textx = TargetSelector.getSelectionAsText(source, s1);
            if (textx == null) {
                textx = new LiteralText("");
            }
        } else if (text instanceof LiteralText) {
            textx = new LiteralText(((LiteralText)text).getRawString());
        } else {
            if (!(text instanceof TranslatableText)) {
                return text;
            }

            Object[] aobject = ((TranslatableText)text).getArgs();

            for (int i = 0; i < aobject.length; i++) {
                Object object = aobject[i];
                if (object instanceof Text) {
                    aobject[i] = updateForEntity(source, (Text)object, entity);
                }
            }

            textx = new TranslatableText(((TranslatableText)text).getKey(), aobject);
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
}
