package net.minecraft.server.command;

import java.util.List;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;

public class GameRuleCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "gamerule";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.gamerule.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        GameRules gamerules = this.getGameRules();
        String s = args.length > 0 ? args[0] : "";
        String s1 = args.length > 1 ? parseString(args, 1) : "";
        switch (args.length) {
            case 0:
                source.sendMessage(new LiteralText(listArgs(gamerules.getAll())));
                break;
            case 1:
                if (!gamerules.contains(s)) {
                    throw new CommandException("commands.gamerule.norule", s);
                }

                String s2 = gamerules.get(s);
                source.sendMessage(new LiteralText(s).append(" = ").append(s2));
                source.addResult(CommandResults.Type.QUERY_RESULT, gamerules.getInt(s));
                break;
            default:
                if (gamerules.isType(s, GameRules.Type.BOOLEAN_VALUE) && !"true".equals(s1) && !"false".equals(s1)) {
                    throw new CommandException("commands.generic.boolean.invalid", s1);
                }

                gamerules.set(s, s1);
                dispatchEntityEvent(gamerules, s);
                sendSuccess(source, this, "commands.gamerule.success");
        }
    }

    public static void dispatchEntityEvent(GameRules gameRules, String ruleName) {
        if ("reducedDebugInfo".equals(ruleName)) {
            byte b0 = (byte)(gameRules.getBoolean(ruleName) ? 22 : 23);

            for (ServerPlayerEntity serverplayerentity : MinecraftServer.getInstance().getPlayerManager().getAll()) {
                serverplayerentity.networkHandler.sendPacket(new EntityEventS2CPacket(serverplayerentity, b0));
            }
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, this.getGameRules().getAll());
        }

        if (args.length == 2) {
            GameRules gamerules = this.getGameRules();
            if (gamerules.isType(args[0], GameRules.Type.BOOLEAN_VALUE)) {
                return suggestMatching(args, "true", "false");
            }
        }

        return null;
    }

    private GameRules getGameRules() {
        return MinecraftServer.getInstance().getWorld(0).getGameRules();
    }
}
