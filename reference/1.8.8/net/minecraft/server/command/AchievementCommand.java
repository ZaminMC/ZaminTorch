package net.minecraft.server.command;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.stat.achievement.AchievementStat;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.util.math.BlockPos;

public class AchievementCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "achievement";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.achievement.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new IncorrectUsageException("commands.achievement.usage");
        }

        final Stat stat = Stats.byKey(args[1]);
        if (stat == null && !args[1].equals("*")) {
            throw new CommandException("commands.achievement.unknownAchievement", args[1]);
        }

        final ServerPlayerEntity serverplayerentity = args.length >= 3 ? parsePlayer(source, args[2]) : asPlayer(source);
        boolean flag = args[0].equalsIgnoreCase("give");
        boolean flag1 = args[0].equalsIgnoreCase("take");
        if (flag || flag1) {
            if (stat == null) {
                if (flag) {
                    for (AchievementStat achievementstat4 : Achievements.ALL) {
                        serverplayerentity.incrementStat(achievementstat4);
                    }

                    sendSuccess(source, this, "commands.achievement.give.success.all", serverplayerentity.getName());
                } else if (flag1) {
                    for (AchievementStat achievementstat5 : Lists.reverse(Achievements.ALL)) {
                        serverplayerentity.clearStat(achievementstat5);
                    }

                    sendSuccess(source, this, "commands.achievement.take.success.all", serverplayerentity.getName());
                }
            } else {
                if (stat instanceof AchievementStat) {
                    AchievementStat achievementstat = (AchievementStat)stat;
                    if (flag) {
                        if (serverplayerentity.getStats().hasAchievement(achievementstat)) {
                            throw new CommandException("commands.achievement.alreadyHave", serverplayerentity.getName(), stat.getNameForChat());
                        }

                        List<AchievementStat> list = Lists.newArrayList();

                        while (achievementstat.parent != null && !serverplayerentity.getStats().hasAchievement(achievementstat.parent)) {
                            list.add(achievementstat.parent);
                            achievementstat = achievementstat.parent;
                        }

                        for (AchievementStat achievementstat1 : Lists.reverse(list)) {
                            serverplayerentity.incrementStat(achievementstat1);
                        }
                    } else if (flag1) {
                        if (!serverplayerentity.getStats().hasAchievement(achievementstat)) {
                            throw new CommandException("commands.achievement.dontHave", serverplayerentity.getName(), stat.getNameForChat());
                        }

                        List<AchievementStat> list1 = Lists.newArrayList(Iterators.filter(Achievements.ALL.iterator(), new Predicate<AchievementStat>() {
                            public boolean apply(AchievementStat achievementStat) {
                                return serverplayerentity.getStats().hasAchievement(achievementStat) && achievementStat != stat;
                            }
                        }));
                        List<AchievementStat> list2 = Lists.newArrayList(list1);

                        for (AchievementStat achievementstat2 : list1) {
                            AchievementStat achievementstat3 = achievementstat2;
                            boolean flag2 = false;

                            while (achievementstat3 != null) {
                                if (achievementstat3 == stat) {
                                    flag2 = true;
                                }

                                achievementstat3 = achievementstat3.parent;
                            }

                            if (!flag2) {
                                for (AchievementStat achievementstat7 = achievementstat2; achievementstat7 != null; achievementstat7 = achievementstat7.parent) {
                                    list2.remove(achievementstat2);
                                }
                            }
                        }

                        for (AchievementStat achievementstat6 : list2) {
                            serverplayerentity.clearStat(achievementstat6);
                        }
                    }
                }

                if (flag) {
                    serverplayerentity.incrementStat(stat);
                    sendSuccess(source, this, "commands.achievement.give.success.one", serverplayerentity.getName(), stat.getNameForChat());
                } else if (flag1) {
                    serverplayerentity.clearStat(stat);
                    sendSuccess(source, this, "commands.achievement.take.success.one", stat.getNameForChat(), serverplayerentity.getName());
                }
            }
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, "give", "take");
        }

        if (args.length != 2) {
            return args.length == 3 ? suggestMatching(args, MinecraftServer.getInstance().getPlayerNames()) : null;
        }

        List<String> list = Lists.newArrayList();

        for (Stat stat : Stats.ALL) {
            list.add(stat.key);
        }

        return suggestMatching(args, list);
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 2;
    }
}
