package net.minecraft.server.command;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public class TriggerCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "trigger";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.trigger.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 3) {
            throw new IncorrectUsageException("commands.trigger.usage");
        }

        ServerPlayerEntity serverplayerentity;
        if (source instanceof ServerPlayerEntity) {
            serverplayerentity = (ServerPlayerEntity)source;
        } else {
            Entity entity = source.asEntity();
            if (!(entity instanceof ServerPlayerEntity)) {
                throw new CommandException("commands.trigger.invalidPlayer");
            }

            serverplayerentity = (ServerPlayerEntity)entity;
        }

        Scoreboard scoreboard = MinecraftServer.getInstance().getWorld(0).getScoreboard();
        ScoreboardObjective scoreboardobjective = scoreboard.getObjective(args[0]);
        if (scoreboardobjective != null && scoreboardobjective.getCriterion() == ScoreboardCriterion.TRIGGER) {
            int i = parseInt(args[2]);
            if (!scoreboard.hasScore(serverplayerentity.getName(), scoreboardobjective)) {
                throw new CommandException("commands.trigger.invalidObjective", args[0]);
            }

            ScoreboardScore scoreboardscore = scoreboard.getScore(serverplayerentity.getName(), scoreboardobjective);
            if (scoreboardscore.isLocked()) {
                throw new CommandException("commands.trigger.disabled", args[0]);
            }

            if ("set".equals(args[1])) {
                scoreboardscore.set(i);
            } else {
                if (!"add".equals(args[1])) {
                    throw new CommandException("commands.trigger.invalidMode", args[1]);
                }

                scoreboardscore.increase(i);
            }

            scoreboardscore.setLocked(true);
            if (serverplayerentity.interactionManager.isCreative()) {
                sendSuccess(source, this, "commands.trigger.success", args[0], args[1], args[2]);
            }
        } else {
            throw new CommandException("commands.trigger.invalidObjective", args[0]);
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            Scoreboard scoreboard = MinecraftServer.getInstance().getWorld(0).getScoreboard();
            List<String> list = Lists.newArrayList();

            for (ScoreboardObjective scoreboardobjective : scoreboard.getObjectives()) {
                if (scoreboardobjective.getCriterion() == ScoreboardCriterion.TRIGGER) {
                    list.add(scoreboardobjective.getName());
                }
            }

            return suggestMatching(args, list.toArray(new String[list.size()]));
        } else {
            return args.length == 2 ? suggestMatching(args, "add", "set") : null;
        }
    }
}
