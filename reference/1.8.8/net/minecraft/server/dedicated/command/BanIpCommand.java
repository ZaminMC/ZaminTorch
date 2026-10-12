package net.minecraft.server.dedicated.command;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.server.IpBanEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.PlayerNotFoundException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class BanIpCommand extends AbstractCommand {
    public static final Pattern REGEX_PATTERN = Pattern.compile(
        "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"
    );

    @Override
    public String getName() {
        return "ban-ip";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public boolean canUse(CommandSource source) {
        return MinecraftServer.getInstance().getPlayerManager().getIpBans().isEnabled() && super.canUse(source);
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.banip.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length >= 1 && args[0].length() > 1) {
            Text text = args.length >= 2 ? parseText(source, args, 1) : null;
            Matcher matcher = REGEX_PATTERN.matcher(args[0]);
            if (matcher.matches()) {
                this.banIp(source, args[0], text == null ? null : text.getString());
            } else {
                ServerPlayerEntity serverplayerentity = MinecraftServer.getInstance().getPlayerManager().get(args[0]);
                if (serverplayerentity == null) {
                    throw new PlayerNotFoundException("commands.banip.invalid");
                }

                this.banIp(source, serverplayerentity.getIp(), text == null ? null : text.getString());
            }
        } else {
            throw new IncorrectUsageException("commands.banip.usage");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 1 ? suggestMatching(args, MinecraftServer.getInstance().getPlayerNames()) : null;
    }

    protected void banIp(CommandSource commmand, String name, String reason) {
        IpBanEntry ipbanentry = new IpBanEntry(name, null, commmand.getName(), null, reason);
        MinecraftServer.getInstance().getPlayerManager().getIpBans().add(ipbanentry);
        List<ServerPlayerEntity> list = MinecraftServer.getInstance().getPlayerManager().getAtIp(name);
        String[] astring = new String[list.size()];
        int i = 0;

        for (ServerPlayerEntity serverplayerentity : list) {
            serverplayerentity.networkHandler.disconnect("You have been IP banned.");
            astring[i++] = serverplayerentity.getName();
        }

        if (list.isEmpty()) {
            sendSuccess(commmand, this, "commands.banip.success", name);
        } else {
            sendSuccess(commmand, this, "commands.banip.success.players", name, listArgs(astring));
        }
    }
}
