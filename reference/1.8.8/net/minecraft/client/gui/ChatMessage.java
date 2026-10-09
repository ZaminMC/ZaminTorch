package net.minecraft.client.gui;

import net.minecraft.text.Text;

public class ChatMessage {
    private final int timeOfCreation;
    private final Text text;
    private final int id;

    public ChatMessage(int timeOfCreation, Text text, int id) {
        this.text = text;
        this.timeOfCreation = timeOfCreation;
        this.id = id;
    }

    public Text getText() {
        return this.text;
    }

    public int getTimeOfCreation() {
        return this.timeOfCreation;
    }

    public int getId() {
        return this.id;
    }
}
