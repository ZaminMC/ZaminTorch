package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;

public class TitlesS2CPacket implements Packet<ClientPlayPacketHandler> {
    private TitlesS2CPacket.Type type;
    private Text text;
    private int fadeIn;
    private int duration;
    private int fadeOut;

    public TitlesS2CPacket() {
    }

    public TitlesS2CPacket(TitlesS2CPacket.Type type, Text text) {
        this(type, text, -1, -1, -1);
    }

    public TitlesS2CPacket(int fadeIn, int duration, int fadeOut) {
        this(TitlesS2CPacket.Type.TIMES, null, fadeIn, duration, fadeOut);
    }

    public TitlesS2CPacket(TitlesS2CPacket.Type type, Text text, int fadeIn, int duration, int fadeOut) {
        this.type = type;
        this.text = text;
        this.fadeIn = fadeIn;
        this.duration = duration;
        this.fadeOut = fadeOut;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.type = buffer.readEnum(TitlesS2CPacket.Type.class);
        if (this.type == TitlesS2CPacket.Type.TITLE || this.type == TitlesS2CPacket.Type.SUBTITLE) {
            this.text = buffer.readText();
        }

        if (this.type == TitlesS2CPacket.Type.TIMES) {
            this.fadeIn = buffer.readInt();
            this.duration = buffer.readInt();
            this.fadeOut = buffer.readInt();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeEnum(this.type);
        if (this.type == TitlesS2CPacket.Type.TITLE || this.type == TitlesS2CPacket.Type.SUBTITLE) {
            buffer.writeText(this.text);
        }

        if (this.type == TitlesS2CPacket.Type.TIMES) {
            buffer.writeInt(this.fadeIn);
            buffer.writeInt(this.duration);
            buffer.writeInt(this.fadeOut);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleTitles(this);
    }

    public TitlesS2CPacket.Type getType() {
        return this.type;
    }

    public Text getText() {
        return this.text;
    }

    public int getFadeIn() {
        return this.fadeIn;
    }

    public int getDuration() {
        return this.duration;
    }

    public int getFadeOut() {
        return this.fadeOut;
    }

    public enum Type {
        TITLE,
        SUBTITLE,
        TIMES,
        CLEAR,
        RESET;

        public static TitlesS2CPacket.Type byName(String name) {
            for (TitlesS2CPacket.Type titless2cpacket$type : values()) {
                if (titless2cpacket$type.name().equalsIgnoreCase(name)) {
                    return titless2cpacket$type;
                }
            }

            return TITLE;
        }

        public static String[] getNames() {
            String[] astring = new String[values().length];
            int i = 0;

            for (TitlesS2CPacket.Type titless2cpacket$type : values()) {
                astring[i++] = titless2cpacket$type.name().toLowerCase();
            }

            return astring;
        }
    }
}
