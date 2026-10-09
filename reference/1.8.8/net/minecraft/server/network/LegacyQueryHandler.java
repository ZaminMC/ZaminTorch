package net.minecraft.server.network;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.InetSocketAddress;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LegacyQueryHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LogManager.getLogger();
    private ConnectionListener connection;

    public LegacyQueryHandler(ConnectionListener connection) {
        this.connection = connection;
    }

    @Override
    public void channelRead(ChannelHandlerContext channelHandlerContext, Object msg) throws Exception {
        ByteBuf bytebuf = (ByteBuf)msg;
        bytebuf.markReaderIndex();
        boolean flag = true;

        try {
            try {
                if (bytebuf.readUnsignedByte() != 254) {
                    return;
                }

                InetSocketAddress inetsocketaddress = (InetSocketAddress)channelHandlerContext.channel().remoteAddress();
                MinecraftServer minecraftserver = this.connection.getServer();
                int i = bytebuf.readableBytes();
                switch (i) {
                    case 0:
                        LOGGER.debug("Ping: (<1.3.x) from {}:{}", inetsocketaddress.getAddress(), inetsocketaddress.getPort());
                        String s2 = String.format(
                            "%s§%d§%d", minecraftserver.getServerMotd(), minecraftserver.getPlayerCount(), minecraftserver.getMaxPlayerCount()
                        );
                        this.reply(channelHandlerContext, this.toBuffer(s2));
                        break;
                    case 1:
                        if (bytebuf.readUnsignedByte() != 1) {
                            return;
                        }

                        LOGGER.debug("Ping: (1.4-1.5.x) from {}:{}", inetsocketaddress.getAddress(), inetsocketaddress.getPort());
                        String s1 = String.format(
                            "§1\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d",
                            127,
                            minecraftserver.getGameVersion(),
                            minecraftserver.getServerMotd(),
                            minecraftserver.getPlayerCount(),
                            minecraftserver.getMaxPlayerCount()
                        );
                        this.reply(channelHandlerContext, this.toBuffer(s1));
                        break;
                    default:
                        boolean flag1 = bytebuf.readUnsignedByte() == 1;
                        flag1 &= bytebuf.readUnsignedByte() == 250;
                        flag1 &= "MC|PingHost".equals(new String(bytebuf.readBytes(bytebuf.readShort() * 2).array(), Charsets.UTF_16BE));
                        int j = bytebuf.readUnsignedShort();
                        flag1 &= bytebuf.readUnsignedByte() >= 73;
                        flag1 &= 3 + bytebuf.readBytes(bytebuf.readShort() * 2).array().length + 4 == j;
                        flag1 &= bytebuf.readInt() <= 65535;
                        flag1 &= bytebuf.readableBytes() == 0;
                        if (!flag1) {
                            return;
                        }

                        LOGGER.debug("Ping: (1.6) from {}:{}", inetsocketaddress.getAddress(), inetsocketaddress.getPort());
                        String s = String.format(
                            "§1\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d",
                            127,
                            minecraftserver.getGameVersion(),
                            minecraftserver.getServerMotd(),
                            minecraftserver.getPlayerCount(),
                            minecraftserver.getMaxPlayerCount()
                        );
                        ByteBuf bytebuf1 = this.toBuffer(s);

                        try {
                            this.reply(channelHandlerContext, bytebuf1);
                        } finally {
                            bytebuf1.release();
                        }
                }

                bytebuf.release();
                flag = false;
            } catch (RuntimeException runtimeexception) {
            }
        } finally {
            if (flag) {
                bytebuf.resetReaderIndex();
                channelHandlerContext.channel().pipeline().remove("legacy_query");
                channelHandlerContext.fireChannelRead(msg);
            }
        }
    }

    private void reply(ChannelHandlerContext context, ByteBuf buffer) {
        context.pipeline().firstContext().writeAndFlush(buffer).addListener(ChannelFutureListener.CLOSE);
    }

    private ByteBuf toBuffer(String s) {
        ByteBuf bytebuf = Unpooled.buffer();
        bytebuf.writeByte(255);
        char[] achar = s.toCharArray();
        bytebuf.writeShort(achar.length);

        for (char c0 : achar) {
            bytebuf.writeChar(c0);
        }

        return bytebuf;
    }
}
