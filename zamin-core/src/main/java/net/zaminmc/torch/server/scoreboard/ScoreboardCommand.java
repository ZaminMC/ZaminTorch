package net.zaminmc.torch.server.scoreboard;

import net.zaminmc.torch.server.chat.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * The /scoreboard command (ScoreboardCommand 1:1, op-2): the objectives
 * family (list / add / remove / setdisplay), the players family (list / set
 * / add / remove / reset / enable / test / operation), and the teams family
 * (list / add / remove / empty / join / leave / option), plus the reference's
 * own {@code *} wildcard walk (re-run per score owner).
 *
 * <p>Known engine adaptations (ledgered): the feedback is literal English
 * mirroring the en_US locale (the engine carries no i18n layer — every
 * builtin command does the same); the multi-line list answers join into one
 * line (the command surface returns a single string); entity selectors and
 * the dataTag NBT gate of {@code players set} do not exist in the engine's
 * command surface yet (owner names are plain strings).</p>
 */
public final class ScoreboardCommand {

    /** The chat codes indexed by color id (Formatting's own order). */
    private static final String CHAT_CODES = "0123456789abcdef";

    private final net.zaminmc.torch.server.scoreboard.EngineScoreboard board;

    public ScoreboardCommand(EngineScoreboard board) {
        this.board = board;
    }

    /** @return the feedback line, or null when already answered. */
    public String run(CommandSender sender, String[] args) {
        if (!runWildcard(sender, args)) {
            if (args.length == 0) {
                throw new IllegalArgumentException("Usage: /scoreboard <objectives|players|teams> ...");
            }
            switch (args[0].toLowerCase()) {
                case "objectives" -> objectives(sender, args);
                case "players" -> playersArm(sender, args);
                case "teams" -> teams(sender, args);
                default -> throw new IllegalArgumentException(
                        "Usage: /scoreboard <objectives|players|teams> ...");
            }
        }
        return null; // every arm answers through sendMessage (the reference's own shape)
    }

    // --- the wildcard walk (runWildcard lines 204-245) -----------------------

    private boolean runWildcard(CommandSender sender, String[] args) {
        int wildcardAt = -1;
        for (int i = 0; i < args.length; i++) {
            if ("*".equals(args[i])) {
                if (wildcardAt >= 0) {
                    throw new IllegalArgumentException("Can only have one wildcard");
                }
                wildcardAt = i;
            }
        }
        if (wildcardAt < 0) {
            return false;
        }
        List<String> owners = new ArrayList<>(board.getScoreOwners());
        List<String> applied = new ArrayList<>();
        String original = args[wildcardAt];
        for (String owner : owners) {
            args[wildcardAt] = owner;
            try {
                run(sender, args);
                applied.add(owner);
            } catch (RuntimeException failure) {
                sender.sendMessage("\u00a7c" + failure.getMessage());
            }
        }
        args[wildcardAt] = original;
        if (applied.isEmpty()) {
            throw new IllegalArgumentException("All matches failed");
        }
        sender.sendMessage(applied.size() + " matched");
        return true;
    }

    // --- objectives (lines 46-77 + 273-308 + 564-613) ------------------------

    private void objectives(CommandSender sender, String[] args) {
        if (args.length == 1) {
            throw new IllegalArgumentException(
                    "Usage: /scoreboard objectives <list|add|remove|setdisplay> ...");
        }
        switch (args[1].toLowerCase()) {
            case "list" -> listObjectives(sender);
            case "add" -> addObjective(sender, args);
            case "remove" -> {
                if (args.length != 3) {
                    throw new IllegalArgumentException("Usage: /scoreboard objectives remove <name>");
                }
                ScoreboardObjective objective = objective(args[2], false);
                board.removeObjective(objective);
                sender.sendMessage("Removed objective '" + objective.name() + "'");
            }
            case "setdisplay" -> setDisplay(sender, args);
            default -> throw new IllegalArgumentException(
                    "Usage: /scoreboard objectives <list|add|remove|setdisplay> ...");
        }
    }

    private void listObjectives(CommandSender sender) {
        List<ScoreboardObjective> all = new ArrayList<>(board.getObjectives());
        if (all.isEmpty()) {
            throw new IllegalArgumentException("There are no objectives on the scoreboard");
        }
        StringBuilder text = new StringBuilder("There are " + all.size()
                + " objectives on the scoreboard:");
        for (ScoreboardObjective objective : all) {
            text.append(" - ").append(objective.name()).append(": shows as '")
                    .append(objective.displayName()).append("' and is type '")
                    .append(objective.criterion().name()).append("'");
        }
        sender.sendMessage(text.toString());
    }

    private void addObjective(CommandSender sender, String[] args) {
        if (args.length < 4) {
            throw new IllegalArgumentException(
                    "Usage: /scoreboard objectives add <name> <criterion> [displayName]");
        }
        String name = args[2];
        ScoreboardCriterion criterion = ScoreboardCriterion.BY_NAME.get(args[3]);
        if (criterion == null) {
            throw new IllegalArgumentException("Unknown criterion name '" + args[3] + "'");
        }
        if (board.getObjective(name) != null) {
            throw new IllegalArgumentException(
                    "An objective with the name '" + name + "' already exists");
        }
        if (name.length() > 16) {
            throw new IllegalArgumentException(
                    "The name '" + name + "' is too long (maximum 16 characters)");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Usage: /scoreboard objectives add <name> <criterion> [displayName]");
        }
        ScoreboardObjective objective = board.createObjective(name, criterion);
        if (args.length > 4) {
            String display = String.join(" ",
                    java.util.Arrays.copyOfRange(args, 4, args.length));
            if (display.length() > 32) {
                throw new IllegalArgumentException(
                        "The name '" + display + "' is too long (maximum 32 characters)");
            }
            if (!display.isEmpty()) {
                objective.setDisplayName(display);
            }
        }
        sender.sendMessage("Added new objective '" + name + "'");
    }

    private void setDisplay(CommandSender sender, String[] args) {
        if (args.length != 3 && args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: /scoreboard objectives setdisplay <slot> [objective]");
        }
        String slotName = args[2];
        int slot = EngineScoreboard.displaySlot(slotName);
        ScoreboardObjective objective = null;
        if (args.length == 4) {
            objective = objective(args[3], false);
        }
        if (slot < 0) {
            throw new IllegalArgumentException("Unknown display slot '" + slotName + "'");
        }
        board.setDisplayObjective(slot, objective);
        if (objective != null) {
            sender.sendMessage("Set the display objective in slot '"
                    + EngineScoreboard.displayLocation(slot) + "' to '" + objective.name() + "'");
        } else {
            sender.sendMessage("Cleared display objective in slot '"
                    + EngineScoreboard.displayLocation(slot) + "'");
        }
    }

    // --- players (lines 78-143 + 615-796) --------------------------------------

    private void playersArm(CommandSender sender, String[] args) {
        if (args.length == 1) {
            throw new IllegalArgumentException(
                    "Usage: /scoreboard players <list|set|add|remove|reset|enable|test|operation> ...");
        }
        switch (args[1].toLowerCase()) {
            case "list" -> {
                if (args.length > 3) {
                    throw new IllegalArgumentException("Usage: /scoreboard players list [owner]");
                }
                listPlayers(sender, args);
            }
            case "set", "add", "remove" -> {
                if (args.length < 5) {
                    throw new IllegalArgumentException("Usage: /scoreboard players "
                            + args[1].toLowerCase() + " <owner> <objective> <value>");
                }
                setScore(sender, args);
            }
            case "reset" -> {
                if (args.length != 3 && args.length != 4) {
                    throw new IllegalArgumentException(
                            "Usage: /scoreboard players reset <owner> [objective]");
                }
                resetScore(sender, args);
            }
            case "enable" -> {
                if (args.length != 4) {
                    throw new IllegalArgumentException(
                            "Usage: /scoreboard players enable <owner> <objective>");
                }
                enableTrigger(sender, args);
            }
            case "test" -> {
                if (args.length != 5 && args.length != 6) {
                    throw new IllegalArgumentException(
                            "Usage: /scoreboard players test <owner> <objective> <min> [max]");
                }
                testScore(sender, args);
            }
            case "operation" -> {
                if (args.length != 7) {
                    throw new IllegalArgumentException("Usage: /scoreboard players operation "
                            + "<targetOwner> <targetObjective> <operation> "
                            + "<sourceOwner> <sourceObjective>");
                }
                modifyScore(sender, args);
            }
            default -> throw new IllegalArgumentException(
                    "Usage: /scoreboard players <list|set|add|remove|reset|enable|test|operation> ...");
        }
    }

    private void listPlayers(CommandSender sender, String[] args) {
        if (args.length > 2) {
            String owner = args[2];
            var scores = board.getScores(owner);
            if (scores.isEmpty()) {
                throw new IllegalArgumentException(
                        "Player " + owner + " has no scores recorded");
            }
            StringBuilder text = new StringBuilder("Showing " + scores.size() + " tracked score"
                    + (scores.size() == 1 ? "" : "s") + " for " + owner + ":");
            for (ScoreboardScore score : scores.values()) {
                text.append(" - ").append(score.get()).append(" (")
                        .append(score.objective().displayName()).append(")")
                        .append(" [").append(score.objective().name()).append("]");
            }
            sender.sendMessage(text.toString());
        } else {
            List<String> owners = new ArrayList<>(board.getScoreOwners());
            if (owners.isEmpty()) {
                throw new IllegalArgumentException("There are no tracked players on the scoreboard");
            }
            sender.sendMessage("There are " + owners.size() + " tracked players on the scoreboard: "
                    + String.join(", ", owners));
        }
    }

    private void setScore(CommandSender sender, String[] args) {
        String verb = args[1];
        String owner = args[2];
        if (owner.length() > 40) {
            throw new IllegalArgumentException(
                    "The name '" + owner + "' is too long (maximum 40 characters)");
        }
        ScoreboardObjective objective = objective(args[3], true);
        int value = Integer.parseInt(args[4]);
        ScoreboardScore score = board.getScore(owner, objective);
        switch (verb.toLowerCase()) {
            case "set" -> score.set(value);
            case "add" -> score.increase(value);
            default -> score.decrease(value);
        }
        sender.sendMessage("Set " + objective.name() + " of " + owner + " to " + score.get());
    }

    private void resetScore(CommandSender sender, String[] args) {
        String owner = args[2];
        if (args.length > 3) {
            ScoreboardObjective objective = objective(args[3], false);
            board.removeScore(owner, objective);
            sender.sendMessage("Reset score " + objective.name() + " of " + owner);
        } else {
            board.removeScore(owner, null);
            sender.sendMessage("Reset all scores of " + owner);
        }
    }

    private void enableTrigger(CommandSender sender, String[] args) {
        String owner = args[2];
        if (owner.length() > 40) {
            throw new IllegalArgumentException(
                    "The name '" + owner + "' is too long (maximum 40 characters)");
        }
        ScoreboardObjective objective = objective(args[3], false);
        if (objective.criterion() != ScoreboardCriterion.TRIGGER) {
            throw new IllegalArgumentException(
                    "Objective " + objective.name() + " is not a trigger objective");
        }
        board.getScore(owner, objective).setLocked(false);
        sender.sendMessage("Enabled trigger objective " + objective.name() + " for " + owner);
    }

    private void testScore(CommandSender sender, String[] args) {
        String owner = args[2];
        if (owner.length() > 40) {
            throw new IllegalArgumentException(
                    "The name '" + owner + "' is too long (maximum 40 characters)");
        }
        ScoreboardObjective objective = objective(args[3], false);
        if (!board.hasScore(owner, objective)) {
            throw new IllegalArgumentException(
                    "Player " + owner + " has no score for objective " + objective.name());
        }
        int min = args[4].equals("*") ? Integer.MIN_VALUE : Integer.parseInt(args[4]);
        int max = args.length > 5 && !args[5].equals("*")
                ? Integer.parseInt(args[5]) : Integer.MAX_VALUE;
        int value = board.getScore(owner, objective).get();
        if (value >= min && value <= max) {
            sender.sendMessage("Score " + value + " is in range [" + min + ", " + max + "]");
        } else {
            throw new IllegalArgumentException(
                    "Score " + value + " is NOT in range [" + min + ", " + max + "]");
        }
    }

    private void modifyScore(CommandSender sender, String[] args) {
        String targetOwner = args[2];
        ScoreboardObjective targetObjective = objective(args[3], true);
        String operation = args[4];
        String sourceOwner = args[5];
        ScoreboardObjective sourceObjective = objective(args[6], false);
        if (targetOwner.length() > 40 || sourceOwner.length() > 40) {
            throw new IllegalArgumentException(
                    "A name is too long (maximum 40 characters)");
        }
        if (!board.hasScore(sourceOwner, sourceObjective)) {
            throw new IllegalArgumentException("Player " + sourceOwner + " has no score for "
                    + "objective " + sourceObjective.name());
        }
        ScoreboardScore target = board.getScore(targetOwner, targetObjective);
        ScoreboardScore source = board.getScore(sourceOwner, sourceObjective);
        switch (operation) {
            case "+=" -> target.set(target.get() + source.get());
            case "-=" -> target.set(target.get() - source.get());
            case "*=" -> target.set(target.get() * source.get());
            case "/=" -> {
                if (source.get() != 0) {
                    target.set(target.get() / source.get());
                }
            }
            case "%=" -> {
                if (source.get() != 0) {
                    target.set(target.get() % source.get());
                }
            }
            case "=" -> target.set(source.get());
            case "<" -> target.set(Math.min(target.get(), source.get()));
            case ">" -> target.set(Math.max(target.get(), source.get()));
            case "><" -> {
                int swap = target.get();
                target.set(source.get());
                source.set(swap);
            }
            default -> throw new IllegalArgumentException("Invalid operation '" + operation + "'");
        }
        sender.sendMessage("Operation applied");
    }

    // --- teams (lines 149-199 + 309-562) ---------------------------------------

    private void teams(CommandSender sender, String[] args) {
        if (args.length == 1) {
            throw new IllegalArgumentException(
                    "Usage: /scoreboard teams <list|add|remove|empty|join|leave|option> ...");
        }
        switch (args[1].toLowerCase()) {
            case "list" -> {
                if (args.length > 3) {
                    throw new IllegalArgumentException("Usage: /scoreboard teams list [team]");
                }
                listTeams(sender, args);
            }
            case "add" -> addTeam(sender, args);
            case "remove" -> {
                if (args.length != 3) {
                    throw new IllegalArgumentException("Usage: /scoreboard teams remove <name>");
                }
                ScoreboardTeam team = team(args[2]);
                board.removeTeam(team);
                sender.sendMessage("Removed team " + team.name());
            }
            case "empty" -> {
                if (args.length != 3) {
                    throw new IllegalArgumentException("Usage: /scoreboard teams empty <team>");
                }
                emptyTeam(sender, args[2]);
            }
            case "join" -> {
                if (args.length < 4 && sender.player() == null) {
                    throw new IllegalArgumentException(
                            "Usage: /scoreboard teams join <team> [players...]");
                }
                joinTeam(sender, args);
            }
            case "leave" -> {
                if (args.length < 3 && sender.player() == null) {
                    throw new IllegalArgumentException("Usage: /scoreboard teams leave [players...]");
                }
                leaveTeam(sender, args);
            }
            case "option" -> {
                if (args.length != 4 && args.length != 5) {
                    throw new IllegalArgumentException(
                            "Usage: /scoreboard teams option <team> <option> [value]");
                }
                updateTeam(sender, args);
            }
            default -> throw new IllegalArgumentException(
                    "Usage: /scoreboard teams <list|add|remove|empty|join|leave|option> ...");
        }
    }

    private void addTeam(CommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: /scoreboard teams add <name> [displayName]");
        }
        String name = args[2];
        if (board.getTeam(name) != null) {
            throw new IllegalArgumentException("A team with the name '" + name + "' already exists");
        }
        if (name.length() > 16) {
            throw new IllegalArgumentException(
                    "The name '" + name + "' is too long (maximum 16 characters)");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Usage: /scoreboard teams add <name> [displayName]");
        }
        ScoreboardTeam team = board.addTeam(name);
        if (args.length > 3) {
            String display = String.join(" ",
                    java.util.Arrays.copyOfRange(args, 3, args.length));
            if (display.length() > 32) {
                throw new IllegalArgumentException(
                        "The name '" + display + "' is too long (maximum 32 characters)");
            }
            if (!display.isEmpty()) {
                team.setDisplayName(display);
            }
        }
        sender.sendMessage("Added new team '" + name + "'");
    }

    private void listTeams(CommandSender sender, String[] args) {
        if (args.length > 2) {
            ScoreboardTeam team = team(args[2]);
            List<String> members = new ArrayList<>(team.members());
            if (members.isEmpty()) {
                throw new IllegalArgumentException(
                        "Team " + team.name() + " has no players");
            }
            sender.sendMessage("Showing " + members.size() + " player"
                    + (members.size() == 1 ? "" : "s") + " on team " + team.name() + ": "
                    + String.join(", ", members));
        } else {
            List<ScoreboardTeam> all = new ArrayList<>(board.getTeams());
            if (all.isEmpty()) {
                throw new IllegalArgumentException("There are no teams registered on the scoreboard");
            }
            StringBuilder text = new StringBuilder("There are " + all.size()
                    + " teams registered on the scoreboard:");
            for (ScoreboardTeam team : all) {
                text.append(" - ").append(team.name()).append(": '").append(team.displayName())
                        .append("' (").append(team.members().size()).append(" players)");
            }
            sender.sendMessage(text.toString());
        }
    }

    private void emptyTeam(CommandSender sender, String teamName) {
        ScoreboardTeam team = team(teamName);
        List<String> members = new ArrayList<>(team.members());
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Team " + team.name() + " is already empty");
        }
        for (String member : members) {
            board.removeMemberFromTeam(member, team);
        }
        sender.sendMessage("Removed " + members.size() + " player"
                + (members.size() == 1 ? "" : "s") + " from team " + team.name());
    }

    private void joinTeam(CommandSender sender, String[] args) {
        String teamName = args[2];
        List<String> joined = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        if (sender.player() != null && args.length == 3) {
            String self = sender.player().name();
            if (board.addMemberToTeam(self, teamName)) {
                joined.add(self);
            } else {
                failed.add(self);
            }
        } else {
            for (int i = 3; i < args.length; i++) {
                String member = args[i];
                if (board.addMemberToTeam(member, teamName)) {
                    joined.add(member);
                } else {
                    failed.add(member);
                }
            }
        }
        if (!joined.isEmpty()) {
            sender.sendMessage("Added " + joined.size() + " player"
                    + (joined.size() == 1 ? "" : "s") + " to team " + teamName + ": "
                    + String.join(", ", joined));
        }
        if (!failed.isEmpty()) {
            throw new IllegalArgumentException("Could not add "
                    + failed.size() + " player" + (failed.size() == 1 ? "" : "s")
                    + " to team " + teamName + ": " + String.join(", ", failed));
        }
    }

    private void leaveTeam(CommandSender sender, String[] args) {
        List<String> left = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        if (sender.player() != null && args.length == 2) {
            String self = sender.player().name();
            if (board.removeMemberFromTeam(self)) {
                left.add(self);
            } else {
                failed.add(self);
            }
        } else {
            for (int i = 2; i < args.length; i++) {
                String member = args[i];
                if (board.removeMemberFromTeam(member)) {
                    left.add(member);
                } else {
                    failed.add(member);
                }
            }
        }
        if (!left.isEmpty()) {
            sender.sendMessage("Removed " + left.size() + " player"
                    + (left.size() == 1 ? "" : "s") + " from their teams: "
                    + String.join(", ", left));
        }
        if (!failed.isEmpty()) {
            throw new IllegalArgumentException("Could not remove "
                    + failed.size() + " player" + (failed.size() == 1 ? "" : "s")
                    + " from their teams: " + String.join(", ", failed));
        }
    }

    private void updateTeam(CommandSender sender, String[] args) {
        ScoreboardTeam team = team(args[2]);
        String option = args[3].toLowerCase();
        // The reference's gate uses equalsIgnoreCase — the lowercased option
        // compares against the lowercase spellings.
        if (!option.equals("color") && !option.equals("friendlyfire")
                && !option.equals("seefriendlyinvisibles")
                && !option.equals("nametagvisibility")
                && !option.equals("deathmessagevisibility")) {
            throw new IllegalArgumentException("Usage: /scoreboard teams option <team> "
                    + "<color|friendlyfire|seeFriendlyInvisibles|nametagVisibility"
                    + "|deathMessageVisibility> <value>");
        }
        if (args.length == 4) {
            throw new IllegalArgumentException("No value given for option " + option);
        }
        String value = args[4];
        switch (option) {
            case "color" -> {
                int colorId = colorIdByName(value);
                if (colorId < 0) {
                    throw new IllegalArgumentException(
                            "No value exists for option color (try: "
                                    + String.join(", ", ScoreboardCriterion.COLORS) + ")");
                }
                // The reference's own walk: the color sets the team color AND
                // decorates the prefix/suffix with the chat codes.
                team.setColorId(colorId);
                team.setPrefix("\u00a7" + CHAT_CODES.charAt(colorId));
                team.setSuffix("\u00a7r");
            }
            case "friendlyfire" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(
                            "No value exists for option friendlyfire (try: true, false)");
                }
                team.setAllowFriendlyFire(value.equalsIgnoreCase("true"));
            }
            case "seefriendlyinvisibles" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(
                            "No value exists for option seeFriendlyInvisibles (try: true, false)");
                }
                team.setShowFriendlyInvisibles(value.equalsIgnoreCase("true"));
            }
            case "nametagvisibility" -> {
                ScoreboardTeam.Visibility visibility = ScoreboardTeam.Visibility.byKey(value);
                if (visibility == null) {
                    throw new IllegalArgumentException("No value exists for option "
                            + "nametagVisibility (try: "
                            + String.join(", ", ScoreboardTeam.Visibility.keys()) + ")");
                }
                team.setNameTagVisibility(visibility);
            }
            default -> {
                ScoreboardTeam.Visibility visibility = ScoreboardTeam.Visibility.byKey(value);
                if (visibility == null) {
                    throw new IllegalArgumentException("No value exists for option "
                            + "deathMessageVisibility (try: "
                            + String.join(", ", ScoreboardTeam.Visibility.keys()) + ")");
                }
                team.setDeathMessageVisibility(visibility);
            }
        }
        sender.sendMessage("Value " + value + " is now set for option " + option
                + " for team " + team.name());
    }

    // --- the shared gates (lines 251-271) ---------------------------------------

    private ScoreboardObjective objective(String name, boolean write) {
        ScoreboardObjective objective = board.getObjective(name);
        if (objective == null) {
            throw new IllegalArgumentException("Unknown objective name '" + name + "'");
        }
        if (write && objective.criterion().isReadOnly()) {
            throw new IllegalArgumentException(
                    "The objective '" + name + "' is read-only");
        }
        return objective;
    }

    private ScoreboardTeam team(String name) {
        ScoreboardTeam team = board.getTeam(name);
        if (team == null) {
            throw new IllegalArgumentException("Unknown team name '" + name + "'");
        }
        return team;
    }

    /** The color id of a color name (-1 = unknown). */
    private static int colorIdByName(String name) {
        for (int i = 0; i < ScoreboardCriterion.COLORS.length; i++) {
            if (ScoreboardCriterion.COLORS[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}
