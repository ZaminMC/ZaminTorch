package net.zaminmc.torch.server.text;

import java.util.List;

/**
 * The 1.8.8 text component (Text.java 1:1, the Serializer lifted to
 * {@link TextJson} over the engine's dependency-free JSON walk).
 */
public interface Text extends Iterable<Text> {
    Text setStyle(Style style);

    Style getStyle();

    Text append(String text);

    Text append(Text text);

    String getContent();

    String getString();

    String getFormattedString();

    List<Text> getSiblings();

    Text copy();
}
