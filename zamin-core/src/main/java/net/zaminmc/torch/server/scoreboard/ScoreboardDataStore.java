package net.zaminmc.torch.server.scoreboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The scoreboard's persistence (the reference's SavedScoreboardData /
 * scoreboard.dat role, in the engine's dependency-free JSON convention):
 * objectives (name, criterion, display name, render type), teams (every
 * field + the members), the 19 display slots, and every score cell.
 *
 * <p>Save on the shutdown walk and /save; load at boot before the first
 * player can join (the broadcast walks restore into an empty world — the
 * displayed-objectives tracking rebuilds exactly like a live display).</p>
 */
public final class ScoreboardDataStore {

    private final Path file;
    private final EngineScoreboard board;

    public ScoreboardDataStore(Path file, EngineScoreboard board) {
        this.file = file;
        this.board = board;
    }

    /** Restores the model from the file (a missing file = a fresh board). */
    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return; // a corrupt file degrades to a fresh board, never a boot failure
        }
        // Objectives first (teams/scores reference them).
        for (String block : blocks(json, "objective")) {
            String name = stringField(block, "name");
            String criterion = stringField(block, "criterion");
            String display = stringField(block, "displayName");
            String render = stringField(block, "renderType");
            if (name == null || criterion == null) {
                continue;
            }
            ScoreboardCriterion parsed = ScoreboardCriterion.BY_NAME.get(criterion);
            if (parsed == null) {
                continue; // the criterion left the registry: the objective is dropped
            }
            try {
                ScoreboardObjective objective = board.createObjective(name, parsed);
                if (display != null && !display.isEmpty()) {
                    objective.setDisplayName(display);
                }
                if ("hearts".equals(render)) {
                    objective.setRenderType(ScoreboardCriterion.RenderType.HEARTS);
                }
            } catch (RuntimeException ignored) {
                // a corrupt row degrades to skip
            }
        }
        for (String block : blocks(json, "team")) {
            String name = stringField(block, "name");
            if (name == null) {
                continue;
            }
            try {
                ScoreboardTeam team = board.addTeam(name);
                String display = stringField(block, "displayName");
                if (display != null && !display.isEmpty()) {
                    team.setDisplayName(display);
                }
                String prefix = stringField(block, "prefix");
                if (prefix != null) {
                    team.setPrefix(prefix);
                }
                String suffix = stringField(block, "suffix");
                if (suffix != null) {
                    team.setSuffix(suffix);
                }
                team.setAllowFriendlyFire(intField(block, "friendlyFire", 1) == 1);
                team.setShowFriendlyInvisibles(intField(block, "seeInvisibles", 1) == 1);
                String nameTag = stringField(block, "nameTagVisibility");
                if (nameTag != null) {
                    ScoreboardTeam.Visibility visibility = ScoreboardTeam.Visibility.byKey(nameTag);
                    if (visibility != null) {
                        team.setNameTagVisibility(visibility);
                    }
                }
                String deathMessage = stringField(block, "deathMessageVisibility");
                if (deathMessage != null) {
                    ScoreboardTeam.Visibility visibility =
                            ScoreboardTeam.Visibility.byKey(deathMessage);
                    if (visibility != null) {
                        team.setDeathMessageVisibility(visibility);
                    }
                }
                team.setColorId(intField(block, "color", -1));
                for (String member : membersOf(block)) {
                    board.addMemberToTeam(member, name);
                }
            } catch (RuntimeException ignored) {
                // a corrupt row degrades to skip
            }
        }
        for (String block : blocks(json, "slot")) {
            int slot = intField(block, "slot", -1);
            String objective = stringField(block, "objective");
            if (slot >= 0 && slot < EngineScoreboard.DISPLAY_SLOTS && objective != null) {
                ScoreboardObjective parsed = board.getObjective(objective);
                if (parsed != null) {
                    board.setDisplayObjective(slot, parsed);
                }
            }
        }
        for (String block : blocks(json, "score")) {
            String owner = stringField(block, "owner");
            String objective = stringField(block, "objective");
            int value = intField(block, "value", 0);
            if (owner == null || objective == null) {
                continue;
            }
            ScoreboardObjective parsed = board.getObjective(objective);
            if (parsed != null) {
                board.getScore(owner, parsed).set(value);
            }
        }
    }

    /** Persists the model. */
    public void save() {
        StringBuilder json = new StringBuilder("{\n\"objectives\": [\n");
        List<ScoreboardObjective> objectives = List.copyOf(board.getObjectives());
        for (int i = 0; i < objectives.size(); i++) {
            ScoreboardObjective o = objectives.get(i);
            json.append("  {\"name\": ").append(quote(o.name()))
                    .append(", \"criterion\": ").append(quote(o.criterion().name()))
                    .append(", \"displayName\": ").append(quote(o.displayName()))
                    .append(", \"renderType\": ").append(quote(o.renderType().key()))
                    .append('}').append(i < objectives.size() - 1 ? "," : "").append('\n');
        }
        json.append("],\n\"teams\": [\n");
        List<ScoreboardTeam> teams = List.copyOf(board.getTeams());
        for (int i = 0; i < teams.size(); i++) {
            ScoreboardTeam t = teams.get(i);
            json.append("  {\"name\": ").append(quote(t.name()))
                    .append(", \"displayName\": ").append(quote(t.displayName()))
                    .append(", \"prefix\": ").append(quote(t.prefix()))
                    .append(", \"suffix\": ").append(quote(t.suffix()))
                    .append(", \"friendlyFire\": ").append(t.allowFriendlyFire() ? 1 : 0)
                    .append(", \"seeInvisibles\": ").append(t.showFriendlyInvisibles() ? 1 : 0)
                    .append(", \"nameTagVisibility\": ")
                    .append(quote(t.nameTagVisibility().key()))
                    .append(", \"deathMessageVisibility\": ")
                    .append(quote(t.deathMessageVisibility().key()))
                    .append(", \"color\": ").append(t.colorId())
                    .append(", \"members\": [");
            List<String> members = List.copyOf(t.members());
            for (int m = 0; m < members.size(); m++) {
                json.append(quote(members.get(m))).append(m < members.size() - 1 ? ", " : "");
            }
            json.append("]}").append(i < teams.size() - 1 ? "," : "").append('\n');
        }
        json.append("],\n\"slots\": [\n");
        int slots = 0;
        for (int i = 0; i < EngineScoreboard.DISPLAY_SLOTS; i++) {
            ScoreboardObjective displayed = board.getDisplayObjective(i);
            if (displayed != null) {
                if (slots > 0) {
                    json.append(",\n");
                }
                json.append("  {\"slot\": ").append(i)
                        .append(", \"objective\": ").append(quote(displayed.name())).append('}');
                slots++;
            }
        }
        json.append("\n],\n\"scores\": [\n");
        List<ScoreboardScore> scores = List.copyOf(board.getScores());
        for (int i = 0; i < scores.size(); i++) {
            ScoreboardScore s = scores.get(i);
            json.append("  {\"owner\": ").append(quote(s.owner()))
                    .append(", \"objective\": ").append(quote(s.objective().name()))
                    .append(", \"value\": ").append(s.get())
                    .append('}').append(i < scores.size() - 1 ? "," : "").append('\n');
        }
        json.append("]\n}\n");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Scoreboard save failed: " + e.getMessage(), e);
        }
    }

    // --- the dependency-free JSON walk (the OpStore convention) ---------------

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** The blocks of one array-element type (every flat {...} object). */
    private static List<String> blocks(String json, String marker) {
        List<String> parsed = new java.util.ArrayList<>();
        java.util.regex.Matcher entry = java.util.regex.Pattern
                .compile("\\{[^{}]*\\}")
                .matcher(json);
        while (entry.find()) {
            String block = entry.group();
            if (marker.equals("objective") && block.contains("\"criterion\"")) {
                parsed.add(block);
            } else if (marker.equals("team") && block.contains("\"members\"")) {
                parsed.add(block);
            } else if (marker.equals("slot") && block.contains("\"slot\"")) {
                parsed.add(block);
            } else if (marker.equals("score") && block.contains("\"value\"")
                    && block.contains("\"owner\"")) {
                parsed.add(block);
            }
        }
        return parsed;
    }

    private static String stringField(String block, String field) {
        java.util.regex.Matcher match = java.util.regex.Pattern
                .compile("\"" + field + "\": \"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(block);
        if (!match.find()) {
            return null;
        }
        return match.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static int intField(String block, String field, int fallback) {
        java.util.regex.Matcher match = java.util.regex.Pattern
                .compile("\"" + field + "\": (-?\\d+)")
                .matcher(block);
        return match.find() ? Integer.parseInt(match.group(1)) : fallback;
    }

    private static List<String> membersOf(String block) {
        List<String> members = new java.util.ArrayList<>();
        java.util.regex.Matcher list = java.util.regex.Pattern
                .compile("\"members\": \\[(.*)\\]")
                .matcher(block);
        if (!list.find()) {
            return members;
        }
        java.util.regex.Matcher name = java.util.regex.Pattern
                .compile("\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(list.group(1));
        while (name.find()) {
            members.add(name.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return members;
    }
}
