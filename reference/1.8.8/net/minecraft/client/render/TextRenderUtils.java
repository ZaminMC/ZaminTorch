package net.minecraft.client.render;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class TextRenderUtils {
    public static String prepareText(String text, boolean allowFormatting) {
        return !allowFormatting && !Minecraft.getInstance().options.chatColors ? Formatting.strip(text) : text;
    }

    public static List<Text> wrapText(Text text, int width, TextRenderer textRenderer, boolean stripLeadingSpaces, boolean allowFormatting) {
        int i = 0;
        Text textx = new LiteralText("");
        List<Text> list = Lists.newArrayList();
        List<Text> list1 = Lists.newArrayList(text);

        for (int j = 0; j < list1.size(); j++) {
            Text text1 = list1.get(j);
            String s = text1.getContent();
            boolean flag = false;
            if (s.contains("\n")) {
                int k = s.indexOf(10);
                String s1 = s.substring(k + 1);
                s = s.substring(0, k + 1);
                LiteralText literaltext = new LiteralText(s1);
                literaltext.setStyle(text1.getStyle().deepCopy());
                list1.add(j + 1, literaltext);
                flag = true;
            }

            String s4 = prepareText(text1.getStyle().asString() + s, allowFormatting);
            String s5 = s4.endsWith("\n") ? s4.substring(0, s4.length() - 1) : s4;
            int i1 = textRenderer.getWidth(s5);
            LiteralText literaltext1 = new LiteralText(s5);
            literaltext1.setStyle(text1.getStyle().deepCopy());
            if (i + i1 > width) {
                String s2 = textRenderer.trim(s4, width - i, false);
                String s3 = s2.length() < s4.length() ? s4.substring(s2.length()) : null;
                if (s3 != null && s3.length() > 0) {
                    int l = s2.lastIndexOf(" ");
                    if (l >= 0 && textRenderer.getWidth(s4.substring(0, l)) > 0) {
                        s2 = s4.substring(0, l);
                        if (stripLeadingSpaces) {
                            l++;
                        }

                        s3 = s4.substring(l);
                    } else if (i > 0 && !s4.contains(" ")) {
                        s2 = "";
                        s3 = s4;
                    }

                    LiteralText literaltext2 = new LiteralText(s3);
                    literaltext2.setStyle(text1.getStyle().deepCopy());
                    list1.add(j + 1, literaltext2);
                }

                s4 = s2;
                i1 = textRenderer.getWidth(s4);
                literaltext1 = new LiteralText(s4);
                literaltext1.setStyle(text1.getStyle().deepCopy());
                flag = true;
            }

            if (i + i1 <= width) {
                i += i1;
                textx.append(literaltext1);
            } else {
                flag = true;
            }

            if (flag) {
                list.add(textx);
                i = 0;
                textx = new LiteralText("");
            }
        }

        list.add(textx);
        return list;
    }
}
