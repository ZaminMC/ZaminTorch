package net.minecraft.scoreboard;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.Formatting;

public class Scoreboard {
    private final Map<String, ScoreboardObjective> objectivesByName = Maps.newHashMap();
    private final Map<ScoreboardCriterion, List<ScoreboardObjective>> objectivesByCriterion = Maps.newHashMap();
    private final Map<String, Map<ScoreboardObjective, ScoreboardScore>> scores = Maps.newHashMap();
    private final ScoreboardObjective[] displayObjectives = new ScoreboardObjective[19];
    private final Map<String, Team> teamsByName = Maps.newHashMap();
    private final Map<String, Team> teamsByMember = Maps.newHashMap();
    private static String[] displayLocations = null;

    public ScoreboardObjective getObjective(String name) {
        return this.objectivesByName.get(name);
    }

    public ScoreboardObjective createObjective(String name, ScoreboardCriterion criterion) {
        if (name.length() > 16) {
            throw new IllegalArgumentException("The objective name '" + name + "' is too long!");
        }

        ScoreboardObjective scoreboardobjective = this.getObjective(name);
        if (scoreboardobjective != null) {
            throw new IllegalArgumentException("An objective with the name '" + name + "' already exists!");
        }

        scoreboardobjective = new ScoreboardObjective(this, name, criterion);
        List<ScoreboardObjective> list = this.objectivesByCriterion.get(criterion);
        if (list == null) {
            list = Lists.newArrayList();
            this.objectivesByCriterion.put(criterion, list);
        }

        list.add(scoreboardobjective);
        this.objectivesByName.put(name, scoreboardobjective);
        this.onObjectiveCreated(scoreboardobjective);
        return scoreboardobjective;
    }

    public Collection<ScoreboardObjective> getObjectives(ScoreboardCriterion criterion) {
        Collection<ScoreboardObjective> collection = this.objectivesByCriterion.get(criterion);
        return collection == null ? Lists.newArrayList() : Lists.newArrayList(collection);
    }

    public boolean hasScore(String owner, ScoreboardObjective objective) {
        Map<ScoreboardObjective, ScoreboardScore> map = this.scores.get(owner);
        if (map == null) {
            return false;
        }

        ScoreboardScore scoreboardscore = map.get(objective);
        return scoreboardscore != null;
    }

    public ScoreboardScore getScore(String owner, ScoreboardObjective objective) {
        if (owner.length() > 40) {
            throw new IllegalArgumentException("The player name '" + owner + "' is too long!");
        }

        Map<ScoreboardObjective, ScoreboardScore> map = this.scores.get(owner);
        if (map == null) {
            map = Maps.newHashMap();
            this.scores.put(owner, map);
        }

        ScoreboardScore scoreboardscore = map.get(objective);
        if (scoreboardscore == null) {
            scoreboardscore = new ScoreboardScore(this, objective, owner);
            map.put(objective, scoreboardscore);
        }

        return scoreboardscore;
    }

    public Collection<ScoreboardScore> getScores(ScoreboardObjective objective) {
        List<ScoreboardScore> list = Lists.newArrayList();

        for (Map<ScoreboardObjective, ScoreboardScore> map : this.scores.values()) {
            ScoreboardScore scoreboardscore = map.get(objective);
            if (scoreboardscore != null) {
                list.add(scoreboardscore);
            }
        }

        Collections.sort(list, ScoreboardScore.COMPARATOR);
        return list;
    }

    public Collection<ScoreboardObjective> getObjectives() {
        return this.objectivesByName.values();
    }

    public Collection<String> getScoreOwners() {
        return this.scores.keySet();
    }

    public void removeScore(String owner, ScoreboardObjective objective) {
        if (objective == null) {
            Map<ScoreboardObjective, ScoreboardScore> map = this.scores.remove(owner);
            if (map != null) {
                this.onScoresRemoved(owner);
            }
        } else {
            Map<ScoreboardObjective, ScoreboardScore> map2 = this.scores.get(owner);
            if (map2 != null) {
                ScoreboardScore scoreboardscore = map2.remove(objective);
                if (map2.size() < 1) {
                    Map<ScoreboardObjective, ScoreboardScore> map1 = this.scores.remove(owner);
                    if (map1 != null) {
                        this.onScoresRemoved(owner);
                    }
                } else if (scoreboardscore != null) {
                    this.onScoreRemoved(owner, objective);
                }
            }
        }
    }

    public Collection<ScoreboardScore> getScores() {
        Collection<Map<ScoreboardObjective, ScoreboardScore>> collection = this.scores.values();
        List<ScoreboardScore> list = Lists.newArrayList();

        for (Map<ScoreboardObjective, ScoreboardScore> map : collection) {
            list.addAll(map.values());
        }

        return list;
    }

    public Map<ScoreboardObjective, ScoreboardScore> getScores(String owner) {
        Map<ScoreboardObjective, ScoreboardScore> map = this.scores.get(owner);
        if (map == null) {
            map = Maps.newHashMap();
        }

        return map;
    }

    public void removeObjective(ScoreboardObjective objective) {
        this.objectivesByName.remove(objective.getName());

        for (int i = 0; i < 19; i++) {
            if (this.getDisplayObjective(i) == objective) {
                this.setDisplayObjective(i, null);
            }
        }

        List<ScoreboardObjective> list = this.objectivesByCriterion.get(objective.getCriterion());
        if (list != null) {
            list.remove(objective);
        }

        for (Map<ScoreboardObjective, ScoreboardScore> map : this.scores.values()) {
            map.remove(objective);
        }

        this.onObjectiveRemoved(objective);
    }

    public void setDisplayObjective(int slot, ScoreboardObjective objective) {
        this.displayObjectives[slot] = objective;
    }

    public ScoreboardObjective getDisplayObjective(int slot) {
        return this.displayObjectives[slot];
    }

    public Team getTeam(String name) {
        return this.teamsByName.get(name);
    }

    public Team addTeam(String name) {
        if (name.length() > 16) {
            throw new IllegalArgumentException("The team name '" + name + "' is too long!");
        }

        Team team = this.getTeam(name);
        if (team != null) {
            throw new IllegalArgumentException("A team with the name '" + name + "' already exists!");
        }

        team = new Team(this, name);
        this.teamsByName.put(name, team);
        this.onTeamAdded(team);
        return team;
    }

    public void removeTeam(Team team) {
        this.teamsByName.remove(team.getName());

        for (String s : team.getMembers()) {
            this.teamsByMember.remove(s);
        }

        this.onTeamRemoved(team);
    }

    public boolean addMemberToTeam(String member, String teamName) {
        if (member.length() > 40) {
            throw new IllegalArgumentException("The player name '" + member + "' is too long!");
        }

        if (!this.teamsByName.containsKey(teamName)) {
            return false;
        }

        Team team = this.getTeam(teamName);
        if (this.getTeamOfMember(member) != null) {
            this.removeMemberFromTeam(member);
        }

        this.teamsByMember.put(member, team);
        team.getMembers().add(member);
        return true;
    }

    public boolean removeMemberFromTeam(String member) {
        Team team = this.getTeamOfMember(member);
        if (team != null) {
            this.removeMemberFromTeam(member, team);
            return true;
        } else {
            return false;
        }
    }

    public void removeMemberFromTeam(String member, Team team) {
        if (this.getTeamOfMember(member) != team) {
            throw new IllegalStateException("Player is either on another team or not on any team. Cannot remove from team '" + team.getName() + "'.");
        }

        this.teamsByMember.remove(member);
        team.getMembers().remove(member);
    }

    public Collection<String> getTeamNames() {
        return this.teamsByName.keySet();
    }

    public Collection<Team> getTeams() {
        return this.teamsByName.values();
    }

    public Team getTeamOfMember(String playerName) {
        return this.teamsByMember.get(playerName);
    }

    public void onObjectiveCreated(ScoreboardObjective objective) {
    }

    public void onObjectiveUpdated(ScoreboardObjective objective) {
    }

    public void onObjectiveRemoved(ScoreboardObjective objective) {
    }

    public void onScoreUpdated(ScoreboardScore score) {
    }

    public void onScoresRemoved(String owner) {
    }

    public void onScoreRemoved(String owner, ScoreboardObjective objective) {
    }

    public void onTeamAdded(Team team) {
    }

    public void onTeamUpdated(Team team) {
    }

    public void onTeamRemoved(Team team) {
    }

    public static String getDisplayLocation(int slot) {
        switch (slot) {
            case 0:
                return "list";
            case 1:
                return "sidebar";
            case 2:
                return "belowName";
            default:
                if (slot >= 3 && slot <= 18) {
                    Formatting formatting = Formatting.byId(slot - 3);
                    if (formatting != null && formatting != Formatting.RESET) {
                        return "sidebar.team." + formatting.getName();
                    }
                }

                return null;
        }
    }

    public static int getDisplaySlot(String location) {
        if (location.equalsIgnoreCase("list")) {
            return 0;
        }

        if (location.equalsIgnoreCase("sidebar")) {
            return 1;
        }

        if (location.equalsIgnoreCase("belowName")) {
            return 2;
        }

        if (location.startsWith("sidebar.team.")) {
            String s = location.substring("sidebar.team.".length());
            Formatting formatting = Formatting.byName(s);
            if (formatting != null && formatting.getId() >= 0) {
                return formatting.getId() + 3;
            }
        }

        return -1;
    }

    public static String[] getDisplayLocations() {
        if (displayLocations == null) {
            displayLocations = new String[19];

            for (int i = 0; i < 19; i++) {
                displayLocations[i] = getDisplayLocation(i);
            }
        }

        return displayLocations;
    }

    public void onEntityRemoved(Entity entity) {
        if (entity != null && !(entity instanceof PlayerEntity) && !entity.isAlive()) {
            String s = entity.getUuid().toString();
            this.removeScore(s, null);
            this.removeMemberFromTeam(s);
        }
    }
}
