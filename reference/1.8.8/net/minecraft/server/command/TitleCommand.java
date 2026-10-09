package net.minecraft.server.command;

import com.google.gson.JsonParseException;
import java.util.List;
import net.minecraft.network.packet.s2c.play.TitlesS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.CommandSyntaxException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextUtils;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TitleCommand extends AbstractCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public String getName() {
        return "title";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.title.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new IncorrectUsageException("commands.title.usage");
        }

        if (args.length < 3) {
            if ("title".equals(args[1]) || "subtitle".equals(args[1])) {
                throw new IncorrectUsageException("commands.title.usage.title");
            }

            if ("times".equals(args[1])) {
                throw new IncorrectUsageException("commands.title.usage.times");
            }
        }

        ServerPlayerEntity serverplayerentity = parsePlayer(source, args[0]);
        TitlesS2CPacket.Type titless2cpacket$type = TitlesS2CPacket.Type.byName(args[1]);
        if (titless2cpacket$type != TitlesS2CPacket.Type.CLEAR && titless2cpacket$type != TitlesS2CPacket.Type.RESET) {
            if (titless2cpacket$type == TitlesS2CPacket.Type.TIMES) {
                if (args.length != 5) {
                    throw new IncorrectUsageException("commands.title.usage");
                }

                int i = parseInt(args[2]);
                int j = parseInt(args[3]);
                int k = parseInt(args[4]);
                TitlesS2CPacket titless2cpacket2 = new TitlesS2CPacket(i, j, k);
                serverplayerentity.networkHandler.sendPacket(titless2cpacket2);
                sendSuccess(source, this, "commands.title.success");
            } else {
                if (args.length < 3) {
                    throw new IncorrectUsageException("commands.title.usage");
                }

                String s = parseString(args, 2);

                Text text;
                try {
                    text = Text.Serializer.fromJson(s);
                } catch (JsonParseException jsonparseexception) {
                    Throwable throwable = ExceptionUtils.getRootCause(jsonparseexception);
                    throw new CommandSyntaxException("commands.tellraw.jsonException", throwable == null ? "" : throwable.getMessage());
                }

                TitlesS2CPacket titless2cpacket1 = new TitlesS2CPacket(titless2cpacket$type, TextUtils.updateForEntity(source, text, serverplayerentity));
                serverplayerentity.networkHandler.sendPacket(titless2cpacket1);
                sendSuccess(source, this, "commands.title.success");
            }
        } else {
            if (args.length != 2) {
                throw new IncorrectUsageException("commands.title.usage");
            }

            TitlesS2CPacket titless2cpacket = new TitlesS2CPacket(titless2cpacket$type, null);
            serverplayerentity.networkHandler.sendPacket(titless2cpacket);
            sendSuccess(source, this, "commands.title.success");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
        } else {
            return args.length == 2 ? suggestMatching(args, TitlesS2CPacket.Type.getNames()) : null;
        }
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 0;
    }
}
