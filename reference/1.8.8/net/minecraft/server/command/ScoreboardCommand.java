package net.minecraft.server.command;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.CommandSyntaxException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;

public class ScoreboardCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "scoreboard";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.scoreboard.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (!this.runWildcard(source, args)) {
            if (args.length < 1) {
                throw new IncorrectUsageException("commands.scoreboard.usage");
            }

            if (args[0].equalsIgnoreCase("objectives")) {
                if (args.length == 1) {
                    throw new IncorrectUsageException("commands.scoreboard.objectives.usage");
                }

                if (args[1].equalsIgnoreCase("list")) {
                    this.listObjectives(source);
                } else if (args[1].equalsIgnoreCase("add")) {
                    if (args.length < 4) {
                        throw new IncorrectUsageException("commands.scoreboard.objectives.add.usage");
                    }

                    this.addObjective(source, args, 2);
                } else if (args[1].equalsIgnoreCase("remove")) {
                    if (args.length != 3) {
                        throw new IncorrectUsageException("commands.scoreboard.objectives.remove.usage");
                    }

                    this.removeObjective(source, args[2]);
                } else {
                    if (!args[1].equalsIgnoreCase("setdisplay")) {
                        throw new IncorrectUsageException("commands.scoreboard.objectives.usage");
                    }

                    if (args.length != 3 && args.length != 4) {
                        throw new IncorrectUsageException("commands.scoreboard.objectives.setdisplay.usage");
                    }

                    this.setDisplayObjective(source, args, 2);
                }
            } else if (args[0].equalsIgnoreCase("players")) {
                if (args.length == 1) {
                    throw new IncorrectUsageException("commands.scoreboard.players.usage");
                }

                if (args[1].equalsIgnoreCase("list")) {
                    if (args.length > 3) {
                        throw new IncorrectUsageException("commands.scoreboard.players.list.usage");
                    }

                    this.listPlayers(source, args, 2);
                } else if (args[1].equalsIgnoreCase("add")) {
                    if (args.length < 5) {
                        throw new IncorrectUsageException("commands.scoreboard.players.add.usage");
                    }

                    this.setScore(source, args, 2);
                } else if (args[1].equalsIgnoreCase("remove")) {
                    if (args.length < 5) {
                        throw new IncorrectUsageException("commands.scoreboard.players.remove.usage");
                    }

                    this.setScore(source, args, 2);
                } else if (args[1].equalsIgnoreCase("set")) {
                    if (args.length < 5) {
                        throw new IncorrectUsageException("commands.scoreboard.players.set.usage");
                    }

                    this.setScore(source, args, 2);
                } else if (args[1].equalsIgnoreCase("reset")) {
                    if (args.length != 3 && args.length != 4) {
                        throw new IncorrectUsageException("commands.scoreboard.players.reset.usage");
                    }

                    this.resetScore(source, args, 2);
                } else if (args[1].equalsIgnoreCase("enable")) {
                    if (args.length != 4) {
                        throw new IncorrectUsageException("commands.scoreboard.players.enable.usage");
                    }

                    this.enableTrigger(source, args, 2);
                } else if (args[1].equalsIgnoreCase("test")) {
                    if (args.length != 5 && args.length != 6) {
                        throw new IncorrectUsageException("commands.scoreboard.players.test.usage");
                    }

                    this.testScore(source, args, 2);
                } else {
                    if (!args[1].equalsIgnoreCase("operation")) {
                        throw new IncorrectUsageException("commands.scoreboard.players.usage");
                    }

                    if (args.length != 7) {
                        throw new IncorrectUsageException("commands.scoreboard.players.operation.usage");
                    }

                    this.modifyScore(source, args, 2);
                }
            } else {
                if (!args[0].equalsIgnoreCase("teams")) {
                    throw new IncorrectUsageException("commands.scoreboard.usage");
                }

                if (args.length == 1) {
                    throw new IncorrectUsageException("commands.scoreboard.teams.usage");
                }

                if (args[1].equalsIgnoreCase("list")) {
                    if (args.length > 3) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.list.usage");
                    }

                    this.listTeams(source, args, 2);
                } else if (args[1].equalsIgnoreCase("add")) {
                    if (args.length < 3) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.add.usage");
                    }

                    this.addTeam(source, args, 2);
                } else if (args[1].equalsIgnoreCase("remove")) {
                    if (args.length != 3) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.remove.usage");
                    }

                    this.removeTeam(source, args, 2);
                } else if (args[1].equalsIgnoreCase("empty")) {
                    if (args.length != 3) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.empty.usage");
                    }

                    this.emptyTeam(source, args, 2);
                } else if (args[1].equalsIgnoreCase("join")) {
                    if (args.length < 4 && (args.length != 3 || !(source instanceof PlayerEntity))) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.join.usage");
                    }

                    this.joinTeam(source, args, 2);
                } else if (args[1].equalsIgnoreCase("leave")) {
                    if (args.length < 3 && !(source instanceof PlayerEntity)) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.leave.usage");
                    }

                    this.leaveTeam(source, args, 2);
                } else {
                    if (!args[1].equalsIgnoreCase("option")) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.usage");
                    }

                    if (args.length != 4 && args.length != 5) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.option.usage");
                    }

                    this.updateTeam(source, args, 2);
                }
            }
        }
    }

    private boolean runWildcard(CommandSource source, String[] args) throws CommandException {
        int i = -1;

        for (int j = 0; j < args.length; j++) {
            if (this.hasTargetSelectorAt(args, j) && "*".equals(args[j])) {
                if (i >= 0) {
                    throw new CommandException("commands.scoreboard.noMultiWildcard");
                }

                i = j;
            }
        }

        if (i < 0) {
            return false;
        }

        List<String> list1 = Lists.newArrayList(this.getScoreboard().getScoreOwners());
        String s = args[i];
        List<String> list = Lists.newArrayList();

        for (String s1 : list1) {
            args[i] = s1;

            try {
                this.run(source, args);
                list.add(s1);
            } catch (CommandException commandexception) {
                TranslatableText translatabletext = new TranslatableText(commandexception.getMessage(), commandexception.getArgs());
                translatabletext.getStyle().setColor(Formatting.RED);
                source.sendMessage(translatabletext);
            }
        }

        args[i] = s;
        source.addResult(CommandResults.Type.AFFECTED_ENTITIES, list.size());
        if (list.size() == 0) {
            throw new IncorrectUsageException("commands.scoreboard.allMatchesFailed");
        } else {
            return true;
        }
    }

    protected Scoreboard getScoreboard() {
        return MinecraftServer.getInstance().getWorld(0).getScoreboard();
    }

    protected ScoreboardObjective getObjective(String name, boolean write) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        ScoreboardObjective scoreboardobjective = scoreboard.getObjective(name);
        if (scoreboardobjective == null) {
            throw new CommandException("commands.scoreboard.objectiveNotFound", name);
        } else if (write && scoreboardobjective.getCriterion().isReadOnly()) {
            throw new CommandException("commands.scoreboard.objectiveReadOnly", name);
        } else {
            return scoreboardobjective;
        }
    }

    protected Team getTeam(String name) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            throw new CommandException("commands.scoreboard.teamNotFound", name);
        } else {
            return team;
        }
    }

    protected void addObjective(CommandSource source, String[] args, int index) throws CommandException {
        String s = args[index++];
        String s1 = args[index++];
        Scoreboard scoreboard = this.getScoreboard();
        ScoreboardCriterion scoreboardcriterion = ScoreboardCriterion.BY_NAME.get(s1);
        if (scoreboardcriterion == null) {
            throw new IncorrectUsageException("commands.scoreboard.objectives.add.wrongType", s1);
        }

        if (scoreboard.getObjective(s) != null) {
            throw new CommandException("commands.scoreboard.objectives.add.alreadyExists", s);
        }

        if (s.length() > 16) {
            throw new CommandSyntaxException("commands.scoreboard.objectives.add.tooLong", s, 16);
        }

        if (s.length() == 0) {
            throw new IncorrectUsageException("commands.scoreboard.objectives.add.usage");
        }

        if (args.length > index) {
            String s2 = parseText(source, args, index).getString();
            if (s2.length() > 32) {
                throw new CommandSyntaxException("commands.scoreboard.objectives.add.displayTooLong", s2, 32);
            }

            if (s2.length() > 0) {
                scoreboard.createObjective(s, scoreboardcriterion).setDisplayName(s2);
            } else {
                scoreboard.createObjective(s, scoreboardcriterion);
            }
        } else {
            scoreboard.createObjective(s, scoreboardcriterion);
        }

        sendSuccess(source, this, "commands.scoreboard.objectives.add.success", s);
    }

    protected void addTeam(CommandSource source, String[] args, int index) throws CommandException {
        String s = args[index++];
        Scoreboard scoreboard = this.getScoreboard();
        if (scoreboard.getTeam(s) != null) {
            throw new CommandException("commands.scoreboard.teams.add.alreadyExists", s);
        }

        if (s.length() > 16) {
            throw new CommandSyntaxException("commands.scoreboard.teams.add.tooLong", s, 16);
        }

        if (s.length() == 0) {
            throw new IncorrectUsageException("commands.scoreboard.teams.add.usage");
        }

        if (args.length > index) {
            String s1 = parseText(source, args, index).getString();
            if (s1.length() > 32) {
                throw new CommandSyntaxException("commands.scoreboard.teams.add.displayTooLong", s1, 32);
            }

            if (s1.length() > 0) {
                scoreboard.addTeam(s).setDisplayName(s1);
            } else {
                scoreboard.addTeam(s);
            }
        } else {
            scoreboard.addTeam(s);
        }

        sendSuccess(source, this, "commands.scoreboard.teams.add.success", s);
    }

    protected void updateTeam(CommandSource source, String[] args, int index) throws CommandException {
        Team team = this.getTeam(args[index++]);
        if (team != null) {
            String s = args[index++].toLowerCase();
            if (!s.equalsIgnoreCase("color")
                && !s.equalsIgnoreCase("friendlyfire")
                && !s.equalsIgnoreCase("seeFriendlyInvisibles")
                && !s.equalsIgnoreCase("nametagVisibility")
                && !s.equalsIgnoreCase("deathMessageVisibility")) {
                throw new IncorrectUsageException("commands.scoreboard.teams.option.usage");
            }

            if (args.length == 4) {
                if (s.equalsIgnoreCase("color")) {
                    throw new IncorrectUsageException("commands.scoreboard.teams.option.noValue", s, listArgs(Formatting.getNames(true, false)));
                } else if (s.equalsIgnoreCase("friendlyfire") || s.equalsIgnoreCase("seeFriendlyInvisibles")) {
                    throw new IncorrectUsageException("commands.scoreboard.teams.option.noValue", s, listArgs(Arrays.asList("true", "false")));
                } else if (!s.equalsIgnoreCase("nametagVisibility") && !s.equalsIgnoreCase("deathMessageVisibility")) {
                    throw new IncorrectUsageException("commands.scoreboard.teams.option.usage");
                } else {
                    throw new IncorrectUsageException("commands.scoreboard.teams.option.noValue", s, listArgs(AbstractTeam.Visibility.getKeys()));
                }
            } else {
                String s1 = args[index];
                if (s.equalsIgnoreCase("color")) {
                    Formatting formatting = Formatting.byName(s1);
                    if (formatting == null || formatting.isModifier()) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.option.noValue", s, listArgs(Formatting.getNames(true, false)));
                    }

                    team.setColor(formatting);
                    team.setPrefix(formatting.toString());
                    team.setSuffix(Formatting.RESET.toString());
                } else if (s.equalsIgnoreCase("friendlyfire")) {
                    if (!s1.equalsIgnoreCase("true") && !s1.equalsIgnoreCase("false")) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.option.noValue", s, listArgs(Arrays.asList("true", "false")));
                    }

                    team.setAllowFriendlyFire(s1.equalsIgnoreCase("true"));
                } else if (s.equalsIgnoreCase("seeFriendlyInvisibles")) {
                    if (!s1.equalsIgnoreCase("true") && !s1.equalsIgnoreCase("false")) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.option.noValue", s, listArgs(Arrays.asList("true", "false")));
                    }

                    team.setShowFriendlyInvisibles(s1.equalsIgnoreCase("true"));
                } else if (s.equalsIgnoreCase("nametagVisibility")) {
                    AbstractTeam.Visibility abstractteam$visibility = AbstractTeam.Visibility.byKey(s1);
                    if (abstractteam$visibility == null) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.option.noValue", s, listArgs(AbstractTeam.Visibility.getKeys()));
                    }

                    team.setNameTagVisibility(abstractteam$visibility);
                } else if (s.equalsIgnoreCase("deathMessageVisibility")) {
                    AbstractTeam.Visibility abstractteam$visibility1 = AbstractTeam.Visibility.byKey(s1);
                    if (abstractteam$visibility1 == null) {
                        throw new IncorrectUsageException("commands.scoreboard.teams.option.noValue", s, listArgs(AbstractTeam.Visibility.getKeys()));
                    }

                    team.setDeathMessageVisibility(abstractteam$visibility1);
                }

                sendSuccess(source, this, "commands.scoreboard.teams.option.success", s, team.getName(), s1);
            }
        }
    }

    protected void removeTeam(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        Team team = this.getTeam(args[index]);
        if (team != null) {
            scoreboard.removeTeam(team);
            sendSuccess(source, this, "commands.scoreboard.teams.remove.success", team.getName());
        }
    }

    protected void listTeams(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        if (args.length > index) {
            Team team = this.getTeam(args[index]);
            if (team == null) {
                return;
            }

            Collection<String> collection = team.getMembers();
            source.addResult(CommandResults.Type.QUERY_RESULT, collection.size());
            if (collection.size() <= 0) {
                throw new CommandException("commands.scoreboard.teams.list.player.empty", team.getName());
            }

            TranslatableText translatabletext = new TranslatableText("commands.scoreboard.teams.list.player.count", collection.size(), team.getName());
            translatabletext.getStyle().setColor(Formatting.DARK_GREEN);
            source.sendMessage(translatabletext);
            source.sendMessage(new LiteralText(listArgs(collection.toArray())));
        } else {
            Collection<Team> collection1 = scoreboard.getTeams();
            source.addResult(CommandResults.Type.QUERY_RESULT, collection1.size());
            if (collection1.size() <= 0) {
                throw new CommandException("commands.scoreboard.teams.list.empty");
            }

            TranslatableText translatabletext1 = new TranslatableText("commands.scoreboard.teams.list.count", collection1.size());
            translatabletext1.getStyle().setColor(Formatting.DARK_GREEN);
            source.sendMessage(translatabletext1);

            for (Team team1 : collection1) {
                source.sendMessage(
                    new TranslatableText("commands.scoreboard.teams.list.entry", team1.getName(), team1.getDisplayName(), team1.getMembers().size())
                );
            }
        }
    }

    protected void joinTeam(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        String s = args[index++];
        Set<String> set = Sets.newHashSet();
        Set<String> set1 = Sets.newHashSet();
        if (source instanceof PlayerEntity && index == args.length) {
            String s4 = asPlayer(source).getName();
            if (scoreboard.addMemberToTeam(s4, s)) {
                set.add(s4);
            } else {
                set1.add(s4);
            }
        } else {
            while (index < args.length) {
                String s1 = args[index++];
                if (s1.startsWith("@")) {
                    for (Entity entity : parseEntities(source, s1)) {
                        String s3 = parseEntityName(source, entity.getUuid().toString());
                        if (scoreboard.addMemberToTeam(s3, s)) {
                            set.add(s3);
                        } else {
                            set1.add(s3);
                        }
                    }
                } else {
                    String s2 = parseEntityName(source, s1);
                    if (scoreboard.addMemberToTeam(s2, s)) {
                        set.add(s2);
                    } else {
                        set1.add(s2);
                    }
                }
            }
        }

        if (!set.isEmpty()) {
            source.addResult(CommandResults.Type.AFFECTED_ENTITIES, set.size());
            sendSuccess(source, this, "commands.scoreboard.teams.join.success", set.size(), s, listArgs(set.toArray(new String[set.size()])));
        }

        if (!set1.isEmpty()) {
            throw new CommandException("commands.scoreboard.teams.join.failure", set1.size(), s, listArgs(set1.toArray(new String[set1.size()])));
        }
    }

    protected void leaveTeam(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        Set<String> set = Sets.newHashSet();
        Set<String> set1 = Sets.newHashSet();
        if (source instanceof PlayerEntity && index == args.length) {
            String s3 = asPlayer(source).getName();
            if (scoreboard.removeMemberFromTeam(s3)) {
                set.add(s3);
            } else {
                set1.add(s3);
            }
        } else {
            while (index < args.length) {
                String s = args[index++];
                if (s.startsWith("@")) {
                    for (Entity entity : parseEntities(source, s)) {
                        String s2 = parseEntityName(source, entity.getUuid().toString());
                        if (scoreboard.removeMemberFromTeam(s2)) {
                            set.add(s2);
                        } else {
                            set1.add(s2);
                        }
                    }
                } else {
                    String s1 = parseEntityName(source, s);
                    if (scoreboard.removeMemberFromTeam(s1)) {
                        set.add(s1);
                    } else {
                        set1.add(s1);
                    }
                }
            }
        }

        if (!set.isEmpty()) {
            source.addResult(CommandResults.Type.AFFECTED_ENTITIES, set.size());
            sendSuccess(source, this, "commands.scoreboard.teams.leave.success", set.size(), listArgs(set.toArray(new String[set.size()])));
        }

        if (!set1.isEmpty()) {
            throw new CommandException("commands.scoreboard.teams.leave.failure", set1.size(), listArgs(set1.toArray(new String[set1.size()])));
        }
    }

    protected void emptyTeam(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        Team team = this.getTeam(args[index]);
        if (team != null) {
            Collection<String> collection = Lists.newArrayList(team.getMembers());
            source.addResult(CommandResults.Type.AFFECTED_ENTITIES, collection.size());
            if (collection.isEmpty()) {
                throw new CommandException("commands.scoreboard.teams.empty.alreadyEmpty", team.getName());
            }

            for (String s : collection) {
                scoreboard.removeMemberFromTeam(s, team);
            }

            sendSuccess(source, this, "commands.scoreboard.teams.empty.success", collection.size(), team.getName());
        }
    }

    protected void removeObjective(CommandSource source, String name) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        ScoreboardObjective scoreboardobjective = this.getObjective(name, false);
        scoreboard.removeObjective(scoreboardobjective);
        sendSuccess(source, this, "commands.scoreboard.objectives.remove.success", name);
    }

    protected void listObjectives(CommandSource source) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        Collection<ScoreboardObjective> collection = scoreboard.getObjectives();
        if (collection.size() <= 0) {
            throw new CommandException("commands.scoreboard.objectives.list.empty");
        }

        TranslatableText translatabletext = new TranslatableText("commands.scoreboard.objectives.list.count", collection.size());
        translatabletext.getStyle().setColor(Formatting.DARK_GREEN);
        source.sendMessage(translatabletext);

        for (ScoreboardObjective scoreboardobjective : collection) {
            source.sendMessage(
                new TranslatableText(
                    "commands.scoreboard.objectives.list.entry",
                    scoreboardobjective.getName(),
                    scoreboardobjective.getDisplayName(),
                    scoreboardobjective.getCriterion().getName()
                )
            );
        }
    }

    protected void setDisplayObjective(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        String s = args[index++];
        int i = Scoreboard.getDisplaySlot(s);
        ScoreboardObjective scoreboardobjective = null;
        if (args.length == 4) {
            scoreboardobjective = this.getObjective(args[index], false);
        }

        if (i < 0) {
            throw new CommandException("commands.scoreboard.objectives.setdisplay.invalidSlot", s);
        }

        scoreboard.setDisplayObjective(i, scoreboardobjective);
        if (scoreboardobjective != null) {
            sendSuccess(source, this, "commands.scoreboard.objectives.setdisplay.successSet", Scoreboard.getDisplayLocation(i), scoreboardobjective.getName());
        } else {
            sendSuccess(source, this, "commands.scoreboard.objectives.setdisplay.successCleared", Scoreboard.getDisplayLocation(i));
        }
    }

    protected void listPlayers(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        if (args.length > index) {
            String s = parseEntityName(source, args[index]);
            Map<ScoreboardObjective, ScoreboardScore> map = scoreboard.getScores(s);
            source.addResult(CommandResults.Type.QUERY_RESULT, map.size());
            if (map.size() <= 0) {
                throw new CommandException("commands.scoreboard.players.list.player.empty", s);
            }

            TranslatableText translatabletext = new TranslatableText("commands.scoreboard.players.list.player.count", map.size(), s);
            translatabletext.getStyle().setColor(Formatting.DARK_GREEN);
            source.sendMessage(translatabletext);

            for (ScoreboardScore scoreboardscore : map.values()) {
                source.sendMessage(
                    new TranslatableText(
                        "commands.scoreboard.players.list.player.entry",
                        scoreboardscore.get(),
                        scoreboardscore.getObjective().getDisplayName(),
                        scoreboardscore.getObjective().getName()
                    )
                );
            }
        } else {
            Collection<String> collection = scoreboard.getScoreOwners();
            source.addResult(CommandResults.Type.QUERY_RESULT, collection.size());
            if (collection.size() <= 0) {
                throw new CommandException("commands.scoreboard.players.list.empty");
            }

            TranslatableText translatabletext1 = new TranslatableText("commands.scoreboard.players.list.count", collection.size());
            translatabletext1.getStyle().setColor(Formatting.DARK_GREEN);
            source.sendMessage(translatabletext1);
            source.sendMessage(new LiteralText(listArgs(collection.toArray())));
        }
    }

    protected void setScore(CommandSource source, String[] args, int index) throws CommandException {
        String s = args[index - 1];
        int i = index;
        String s1 = parseEntityName(source, args[index++]);
        if (s1.length() > 40) {
            throw new CommandSyntaxException("commands.scoreboard.players.name.tooLong", s1, 40);
        }

        ScoreboardObjective scoreboardobjective = this.getObjective(args[index++], true);
        int j = s.equalsIgnoreCase("set") ? parseInt(args[index++]) : parseInt(args[index++], 0);
        if (args.length > index) {
            Entity entity = parseEntity(source, args[i]);

            try {
                NbtCompound nbtcompound = SnbtParser.parse(parseString(args, index));
                NbtCompound nbtcompound1 = new NbtCompound();
                entity.writeNbtWithoutId(nbtcompound1);
                if (!NbtUtils.matches(nbtcompound, nbtcompound1, true)) {
                    throw new CommandException("commands.scoreboard.players.set.tagMismatch", s1);
                }
            } catch (NbtException nbtexception) {
                throw new CommandException("commands.scoreboard.players.set.tagError", nbtexception.getMessage());
            }
        }

        Scoreboard scoreboard = this.getScoreboard();
        ScoreboardScore scoreboardscore = scoreboard.getScore(s1, scoreboardobjective);
        if (s.equalsIgnoreCase("set")) {
            scoreboardscore.set(j);
        } else if (s.equalsIgnoreCase("add")) {
            scoreboardscore.increase(j);
        } else {
            scoreboardscore.decrease(j);
        }

        sendSuccess(source, this, "commands.scoreboard.players.set.success", scoreboardobjective.getName(), s1, scoreboardscore.get());
    }

    protected void resetScore(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        String s = parseEntityName(source, args[index++]);
        if (args.length > index) {
            ScoreboardObjective scoreboardobjective = this.getObjective(args[index++], false);
            scoreboard.removeScore(s, scoreboardobjective);
            sendSuccess(source, this, "commands.scoreboard.players.resetscore.success", scoreboardobjective.getName(), s);
        } else {
            scoreboard.removeScore(s, null);
            sendSuccess(source, this, "commands.scoreboard.players.reset.success", s);
        }
    }

    protected void enableTrigger(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        String s = parsePlayerName(source, args[index++]);
        if (s.length() > 40) {
            throw new CommandSyntaxException("commands.scoreboard.players.name.tooLong", s, 40);
        }

        ScoreboardObjective scoreboardobjective = this.getObjective(args[index], false);
        if (scoreboardobjective.getCriterion() != ScoreboardCriterion.TRIGGER) {
            throw new CommandException("commands.scoreboard.players.enable.noTrigger", scoreboardobjective.getName());
        }

        ScoreboardScore scoreboardscore = scoreboard.getScore(s, scoreboardobjective);
        scoreboardscore.setLocked(false);
        sendSuccess(source, this, "commands.scoreboard.players.enable.success", scoreboardobjective.getName(), s);
    }

    protected void testScore(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        String s = parseEntityName(source, args[index++]);
        if (s.length() > 40) {
            throw new CommandSyntaxException("commands.scoreboard.players.name.tooLong", s, 40);
        }

        ScoreboardObjective scoreboardobjective = this.getObjective(args[index++], false);
        if (!scoreboard.hasScore(s, scoreboardobjective)) {
            throw new CommandException("commands.scoreboard.players.test.notFound", scoreboardobjective.getName(), s);
        }

        int i = args[index].equals("*") ? Integer.MIN_VALUE : parseInt(args[index]);
        index++;
        int j = index < args.length && !args[index].equals("*") ? parseInt(args[index], i) : Integer.MAX_VALUE;
        ScoreboardScore scoreboardscore = scoreboard.getScore(s, scoreboardobjective);
        if (scoreboardscore.get() >= i && scoreboardscore.get() <= j) {
            sendSuccess(source, this, "commands.scoreboard.players.test.success", scoreboardscore.get(), i, j);
        } else {
            throw new CommandException("commands.scoreboard.players.test.failed", scoreboardscore.get(), i, j);
        }
    }

    protected void modifyScore(CommandSource source, String[] args, int index) throws CommandException {
        Scoreboard scoreboard = this.getScoreboard();
        String s = parseEntityName(source, args[index++]);
        ScoreboardObjective scoreboardobjective = this.getObjective(args[index++], true);
        String s1 = args[index++];
        String s2 = parseEntityName(source, args[index++]);
        ScoreboardObjective scoreboardobjective1 = this.getObjective(args[index], false);
        if (s.length() > 40) {
            throw new CommandSyntaxException("commands.scoreboard.players.name.tooLong", s, 40);
        }

        if (s2.length() > 40) {
            throw new CommandSyntaxException("commands.scoreboard.players.name.tooLong", s2, 40);
        }

        ScoreboardScore scoreboardscore = scoreboard.getScore(s, scoreboardobjective);
        if (!scoreboard.hasScore(s2, scoreboardobjective1)) {
            throw new CommandException("commands.scoreboard.players.operation.notFound", scoreboardobjective1.getName(), s2);
        }

        ScoreboardScore scoreboardscore1 = scoreboard.getScore(s2, scoreboardobjective1);
        if (s1.equals("+=")) {
            scoreboardscore.set(scoreboardscore.get() + scoreboardscore1.get());
        } else if (s1.equals("-=")) {
            scoreboardscore.set(scoreboardscore.get() - scoreboardscore1.get());
        } else if (s1.equals("*=")) {
            scoreboardscore.set(scoreboardscore.get() * scoreboardscore1.get());
        } else if (s1.equals("/=")) {
            if (scoreboardscore1.get() != 0) {
                scoreboardscore.set(scoreboardscore.get() / scoreboardscore1.get());
            }
        } else if (s1.equals("%=")) {
            if (scoreboardscore1.get() != 0) {
                scoreboardscore.set(scoreboardscore.get() % scoreboardscore1.get());
            }
        } else if (s1.equals("=")) {
            scoreboardscore.set(scoreboardscore1.get());
        } else if (s1.equals("<")) {
            scoreboardscore.set(Math.min(scoreboardscore.get(), scoreboardscore1.get()));
        } else if (s1.equals(">")) {
            scoreboardscore.set(Math.max(scoreboardscore.get(), scoreboardscore1.get()));
        } else {
            if (!s1.equals("><")) {
                throw new CommandException("commands.scoreboard.players.operation.invalidOperation", s1);
            }

            int i = scoreboardscore.get();
            scoreboardscore.set(scoreboardscore1.get());
            scoreboardscore1.set(i);
        }

        sendSuccess(source, this, "commands.scoreboard.players.operation.success");
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, "objectives", "players", "teams");
        }

        if (args[0].equalsIgnoreCase("objectives")) {
            if (args.length == 2) {
                return suggestMatching(args, "list", "add", "remove", "setdisplay");
            }

            if (args[1].equalsIgnoreCase("add")) {
                if (args.length == 4) {
                    Set<String> set = ScoreboardCriterion.BY_NAME.keySet();
                    return suggestMatching(args, set);
                }
            } else if (args[1].equalsIgnoreCase("remove")) {
                if (args.length == 3) {
                    return suggestMatching(args, this.getObjectives(false));
                }
            } else if (args[1].equalsIgnoreCase("setdisplay")) {
                if (args.length == 3) {
                    return suggestMatching(args, Scoreboard.getDisplayLocations());
                }

                if (args.length == 4) {
                    return suggestMatching(args, this.getObjectives(false));
                }
            }
        } else if (args[0].equalsIgnoreCase("players")) {
            if (args.length == 2) {
                return suggestMatching(args, "set", "add", "remove", "reset", "list", "enable", "test", "operation");
            }

            if (!args[1].equalsIgnoreCase("set")
                && !args[1].equalsIgnoreCase("add")
                && !args[1].equalsIgnoreCase("remove")
                && !args[1].equalsIgnoreCase("reset")) {
                if (args[1].equalsIgnoreCase("enable")) {
                    if (args.length == 3) {
                        return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
                    }

                    if (args.length == 4) {
                        return suggestMatching(args, this.getTriggers());
                    }
                } else if (!args[1].equalsIgnoreCase("list") && !args[1].equalsIgnoreCase("test")) {
                    if (args[1].equalsIgnoreCase("operation")) {
                        if (args.length == 3) {
                            return suggestMatching(args, this.getScoreboard().getScoreOwners());
                        }

                        if (args.length == 4) {
                            return suggestMatching(args, this.getObjectives(true));
                        }

                        if (args.length == 5) {
                            return suggestMatching(args, "+=", "-=", "*=", "/=", "%=", "=", "<", ">", "><");
                        }

                        if (args.length == 6) {
                            return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
                        }

                        if (args.length == 7) {
                            return suggestMatching(args, this.getObjectives(false));
                        }
                    }
                } else {
                    if (args.length == 3) {
                        return suggestMatching(args, this.getScoreboard().getScoreOwners());
                    }

                    if (args.length == 4 && args[1].equalsIgnoreCase("test")) {
                        return suggestMatching(args, this.getObjectives(false));
                    }
                }
            } else {
                if (args.length == 3) {
                    return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
                }

                if (args.length == 4) {
                    return suggestMatching(args, this.getObjectives(true));
                }
            }
        } else if (args[0].equalsIgnoreCase("teams")) {
            if (args.length == 2) {
                return suggestMatching(args, "add", "remove", "join", "leave", "empty", "list", "option");
            }

            if (args[1].equalsIgnoreCase("join")) {
                if (args.length == 3) {
                    return suggestMatching(args, this.getScoreboard().getTeamNames());
                }

                if (args.length >= 4) {
                    return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
                }
            } else {
                if (args[1].equalsIgnoreCase("leave")) {
                    return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
                }

                if (!args[1].equalsIgnoreCase("empty") && !args[1].equalsIgnoreCase("list") && !args[1].equalsIgnoreCase("remove")) {
                    if (args[1].equalsIgnoreCase("option")) {
                        if (args.length == 3) {
                            return suggestMatching(args, this.getScoreboard().getTeamNames());
                        }

                        if (args.length == 4) {
                            return suggestMatching(args, "color", "friendlyfire", "seeFriendlyInvisibles", "nametagVisibility", "deathMessageVisibility");
                        }

                        if (args.length == 5) {
                            if (args[3].equalsIgnoreCase("color")) {
                                return suggestMatching(args, Formatting.getNames(true, false));
                            }

                            if (args[3].equalsIgnoreCase("nametagVisibility") || args[3].equalsIgnoreCase("deathMessageVisibility")) {
                                return suggestMatching(args, AbstractTeam.Visibility.getKeys());
                            }

                            if (args[3].equalsIgnoreCase("friendlyfire") || args[3].equalsIgnoreCase("seeFriendlyInvisibles")) {
                                return suggestMatching(args, "true", "false");
                            }
                        }
                    }
                } else if (args.length == 3) {
                    return suggestMatching(args, this.getScoreboard().getTeamNames());
                }
            }
        }

        return null;
    }

    protected List<String> getObjectives(boolean write) {
        Collection<ScoreboardObjective> collection = this.getScoreboard().getObjectives();
        List<String> list = Lists.newArrayList();

        for (ScoreboardObjective scoreboardobjective : collection) {
            if (!write || !scoreboardobjective.getCriterion().isReadOnly()) {
                list.add(scoreboardobjective.getName());
            }
        }

        return list;
    }

    protected List<String> getTriggers() {
        Collection<ScoreboardObjective> collection = this.getScoreboard().getObjectives();
        List<String> list = Lists.newArrayList();

        for (ScoreboardObjective scoreboardobjective : collection) {
            if (scoreboardobjective.getCriterion() == ScoreboardCriterion.TRIGGER) {
                list.add(scoreboardobjective.getName());
            }
        }

        return list;
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        if (!args[0].equalsIgnoreCase("players")) {
            return args[0].equalsIgnoreCase("teams") && index == 2;
        } else {
            return args.length > 1 && args[1].equalsIgnoreCase("operation") ? index == 2 || index == 5 : index == 2;
        }
    }
}
