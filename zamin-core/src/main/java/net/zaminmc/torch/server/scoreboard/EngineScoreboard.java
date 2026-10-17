package net.zaminmc.torch.server.scoreboard;

import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerRegistry;
import net.zaminmc.torch.server.player.PlayerSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The engine's scoreboard (Scoreboard + ServerScoreboard 1:1): the
 * objectives/scores/teams model plus the server's broadcast semantics —
 * score updates publish only for DISPLAYED objectives, display-slot changes
 * drive the start/stop display walks (objective create/remove + the slot
 * pointer + every score), and team changes broadcast to every online wire.
 *
 * <p>Concurrency: engine-owned global state (the ownership table's
 * "scoreboards/teams" row). Mutations ride the tick lane (the command walk
 * and the gameplay drivers run there); the broadcast reads the registry
 * snapshot and writes through each session's thread-safe
 * {@link ClientLink}.</p>
 */
public final class EngineScoreboard {

    /** The number of display slots (0 list, 1 sidebar, 2 belowName, 3-18 team sidebars). */
    public static final int DISPLAY_SLOTS = 19;

    private final PlayerRegistry players;

    private final Map<String, ScoreboardObjective> objectivesByName = new LinkedHashMap<>();
    private final Map<ScoreboardCriterion, List<ScoreboardObjective>> objectivesByCriterion =
            new LinkedHashMap<>();
    private final Map<String, Map<ScoreboardObjective, ScoreboardScore>> scores =
            new LinkedHashMap<>();
    private final ScoreboardObjective[] displayObjectives = new ScoreboardObjective[DISPLAY_SLOTS];
    private final Map<String, ScoreboardTeam> teamsByName = new LinkedHashMap<>();
    private final Map<String, ScoreboardTeam> teamsByMember = new LinkedHashMap<>();
    /** The ServerScoreboard's own tracking: objectives whose scores are live on the wire. */
    private final Set<ScoreboardObjective> displayedObjectives = new LinkedHashSet<>();

    public EngineScoreboard(PlayerRegistry players) {
        this.players = players;
    }

    // --- objectives -----------------------------------------------------------

    public ScoreboardObjective getObjective(String name) {
        return objectivesByName.get(name);
    }

    public ScoreboardObjective createObjective(String name, ScoreboardCriterion criterion) {
        if (name.length() > 16) {
            throw new IllegalArgumentException("The objective name '" + name + "' is too long!");
        }
        if (objectivesByName.get(name) != null) {
            throw new IllegalArgumentException("An objective with the name '" + name
                    + "' already exists!");
        }
        ScoreboardObjective objective = new ScoreboardObjective(this, name, criterion);
        objectivesByCriterion.computeIfAbsent(criterion, k -> new ArrayList<>()).add(objective);
        objectivesByName.put(name, objective);
        onObjectiveCreated(objective);
        return objective;
    }

    public Collection<ScoreboardObjective> getObjectives(ScoreboardCriterion criterion) {
        List<ScoreboardObjective> list = objectivesByCriterion.get(criterion);
        return list == null ? List.of() : List.copyOf(list);
    }

    public Collection<ScoreboardObjective> getObjectives() {
        return List.copyOf(objectivesByName.values());
    }

    public void removeObjective(ScoreboardObjective objective) {
        objectivesByName.remove(objective.name());
        for (int i = 0; i < DISPLAY_SLOTS; i++) {
            if (displayObjectives[i] == objective) {
                setDisplayObjective(i, null);
            }
        }
        List<ScoreboardObjective> list = objectivesByCriterion.get(objective.criterion());
        if (list != null) {
            list.remove(objective);
        }
        for (Map<ScoreboardObjective, ScoreboardScore> map : scores.values()) {
            map.remove(objective);
        }
        onObjectiveRemoved(objective);
    }

    // --- scores ---------------------------------------------------------------

    public boolean hasScore(String owner, ScoreboardObjective objective) {
        Map<ScoreboardObjective, ScoreboardScore> map = scores.get(owner);
        return map != null && map.get(objective) != null;
    }

    public ScoreboardScore getScore(String owner, ScoreboardObjective objective) {
        if (owner.length() > 40) {
            throw new IllegalArgumentException("The player name '" + owner + "' is too long!");
        }
        Map<ScoreboardObjective, ScoreboardScore> map =
                scores.computeIfAbsent(owner, k -> new LinkedHashMap<>());
        return map.computeIfAbsent(objective,
                o -> new ScoreboardScore(this, o, owner));
    }

    /** The objective's scores in the reference's comparator order (the packet emission order). */
    public Collection<ScoreboardScore> getScores(ScoreboardObjective objective) {
        List<ScoreboardScore> list = new ArrayList<>();
        for (Map<ScoreboardObjective, ScoreboardScore> map : scores.values()) {
            ScoreboardScore score = map.get(objective);
            if (score != null) {
                list.add(score);
            }
        }
        list.sort(ScoreboardScore.SCORE_ORDER);
        return list;
    }

    public Collection<ScoreboardScore> getScores() {
        List<ScoreboardScore> list = new ArrayList<>();
        for (Map<ScoreboardObjective, ScoreboardScore> map : scores.values()) {
            list.addAll(map.values());
        }
        return list;
    }

    public Map<ScoreboardObjective, ScoreboardScore> getScores(String owner) {
        Map<ScoreboardObjective, ScoreboardScore> map = scores.get(owner);
        return map == null ? Map.of() : Map.copyOf(map);
    }

    public Collection<String> getScoreOwners() {
        return List.copyOf(scores.keySet());
    }

    public void removeScore(String owner, ScoreboardObjective objective) {
        if (objective == null) {
            Map<ScoreboardObjective, ScoreboardScore> map = scores.remove(owner);
            if (map != null) {
                onScoresRemoved(owner);
            }
        } else {
            Map<ScoreboardObjective, ScoreboardScore> map = scores.get(owner);
            if (map != null) {
                ScoreboardScore score = map.remove(objective);
                if (map.isEmpty()) {
                    scores.remove(owner);
                    onScoresRemoved(owner);
                } else if (score != null) {
                    onScoreRemoved(owner, objective);
                }
            }
        }
    }

    // --- display slots ----------------------------------------------------------

    public void setDisplayObjective(int slot, ScoreboardObjective objective) {
        ScoreboardObjective previous = displayObjectives[slot];
        displayObjectives[slot] = objective;
        // The ServerScoreboard swap walk: a displaced objective that still
        // shows elsewhere just re-points this slot; one that shows nowhere
        // stops displaying (its scores leave the wires).
        if (previous != objective && previous != null) {
            if (getDisplaySlotCount(previous) > 0) {
                broadcastDisplay(slot, objective);
            } else {
                stopDisplayingObjective(previous);
            }
        }
        if (objective != null) {
            if (displayedObjectives.contains(objective)) {
                broadcastDisplay(slot, objective);
            } else {
                startDisplayingObjective(objective);
            }
        }
    }

    public ScoreboardObjective getDisplayObjective(int slot) {
        return displayObjectives[slot];
    }

    /** How many display slots hold the objective (the ServerScoreboard's own count). */
    public int getDisplaySlotCount(ScoreboardObjective objective) {
        int count = 0;
        for (int i = 0; i < DISPLAY_SLOTS; i++) {
            if (displayObjectives[i] == objective) {
                count++;
            }
        }
        return count;
    }

    // --- teams ------------------------------------------------------------------

    public ScoreboardTeam getTeam(String name) {
        return teamsByName.get(name);
    }

    public ScoreboardTeam addTeam(String name) {
        if (name.length() > 16) {
            throw new IllegalArgumentException("The team name '" + name + "' is too long!");
        }
        if (teamsByName.get(name) != null) {
            throw new IllegalArgumentException("A team with the name '" + name
                    + "' already exists!");
        }
        ScoreboardTeam team = new ScoreboardTeam(this, name);
        teamsByName.put(name, team);
        onTeamAdded(team);
        return team;
    }

    public void removeTeam(ScoreboardTeam team) {
        teamsByName.remove(team.name());
        for (String member : team.members()) {
            teamsByMember.remove(member);
        }
        onTeamRemoved(team);
    }

    public boolean addMemberToTeam(String member, String teamName) {
        if (member.length() > 40) {
            throw new IllegalArgumentException("The player name '" + member + "' is too long!");
        }
        if (!teamsByName.containsKey(teamName)) {
            return false;
        }
        ScoreboardTeam team = teamsByName.get(teamName);
        if (teamsByMember.get(member) != null) {
            removeMemberFromTeam(member);
        }
        teamsByMember.put(member, team);
        team.members().add(member);
        broadcastTeamMembers(team, List.of(member), 3); // join
        return true;
    }

    public boolean removeMemberFromTeam(String member) {
        ScoreboardTeam team = teamsByMember.get(member);
        if (team != null) {
            removeMemberFromTeam(member, team);
            return true;
        }
        return false;
    }

    public void removeMemberFromTeam(String member, ScoreboardTeam team) {
        if (teamsByMember.get(member) != team) {
            throw new IllegalStateException("Player is either on another team or not on any"
                    + " team. Cannot remove from team '" + team.name() + "'.");
        }
        teamsByMember.remove(member);
        team.members().remove(member);
        broadcastTeamMembers(team, List.of(member), 4); // leave
    }

    public Collection<String> getTeamNames() {
        return List.copyOf(teamsByName.keySet());
    }

    public Collection<ScoreboardTeam> getTeams() {
        return List.copyOf(teamsByName.values());
    }

    public ScoreboardTeam getTeamOfMember(String playerName) {
        return teamsByMember.get(playerName);
    }

    // --- the ServerScoreboard broadcast hooks -----------------------------------

    void onObjectiveCreated(ScoreboardObjective objective) {
        // The reference broadcasts nothing here: an objective reaches the wire
        // only when a display slot starts showing it (startDisplayingObjective).
    }

    void onObjectiveUpdated(ScoreboardObjective objective) {
        if (displayedObjectives.contains(objective)) {
            broadcastObjective(objective, 2); // display name / render type changed
        }
    }

    void onObjectiveRemoved(ScoreboardObjective objective) {
        if (displayedObjectives.contains(objective)) {
            stopDisplayingObjective(objective);
        }
    }

    void onScoreUpdated(ScoreboardScore score) {
        if (displayedObjectives.contains(score.objective())) {
            broadcastScore(score.owner(), 0 /* CHANGE */,
                    score.objective().name(), score.get());
        }
    }

    void onScoresRemoved(String owner) {
        broadcastScore(owner, 1 /* REMOVE */, "", 0);
    }

    void onScoreRemoved(String owner, ScoreboardObjective objective) {
        broadcastScore(owner, 1 /* REMOVE */, objective.name(), 0);
    }

    void onTeamAdded(ScoreboardTeam team) {
        broadcastTeam(team, 0); // create
    }

    void onTeamUpdated(ScoreboardTeam team) {
        broadcastTeam(team, 2); // update
    }

    void onTeamRemoved(ScoreboardTeam team) {
        broadcastTeam(team, 1); // remove
    }

    // --- the start/stop display walks (ServerScoreboard's own) ------------------

    /** One objective's start-display packet set: create + every slot pointer + every score. */
    private List<ObjectivePacket> createStartDisplayingPackets(ScoreboardObjective objective) {
        List<ObjectivePacket> packets = new ArrayList<>();
        packets.add(new ObjectivePacket(objective, 0));
        for (int i = 0; i < DISPLAY_SLOTS; i++) {
            if (displayObjectives[i] == objective) {
                packets.add(new ObjectivePacket(i, objective));
            }
        }
        for (ScoreboardScore score : getScores(objective)) {
            packets.add(new ObjectivePacket(score));
        }
        return packets;
    }

    private void startDisplayingObjective(ScoreboardObjective objective) {
        List<ObjectivePacket> packets = createStartDisplayingPackets(objective);
        for (PlayerSession session : players.all()) {
            for (ObjectivePacket packet : packets) {
                packet.send(session.link());
            }
        }
        displayedObjectives.add(objective);
    }

    /** One objective's stop-display packet set: remove + every slot pointer cleared. */
    private List<ObjectivePacket> createStopDisplayingPackets(ScoreboardObjective objective) {
        List<ObjectivePacket> packets = new ArrayList<>();
        packets.add(new ObjectivePacket(objective, 1));
        for (int i = 0; i < DISPLAY_SLOTS; i++) {
            if (displayObjectives[i] == objective) {
                packets.add(new ObjectivePacket(i, null));
            }
        }
        return packets;
    }

    private void stopDisplayingObjective(ScoreboardObjective objective) {
        List<ObjectivePacket> packets = createStopDisplayingPackets(objective);
        for (PlayerSession session : players.all()) {
            for (ObjectivePacket packet : packets) {
                packet.send(session.link());
            }
        }
        displayedObjectives.remove(objective);
    }

    /**
     * The join sync (PlayerManager.updateScoreboard lines 168-185): every
     * team first (create packets with the members), then every DISTINCT
     * displayed objective's start-display walk.
     */
    public void syncTo(PlayerSession session) {
        ClientLink link = session.link();
        for (ScoreboardTeam team : getTeams()) {
            link.scoreboardTeam(teamData(team), 0, List.copyOf(team.members()));
        }
        Set<ScoreboardObjective> seen = new LinkedHashSet<>();
        for (int i = 0; i < DISPLAY_SLOTS; i++) {
            ScoreboardObjective objective = displayObjectives[i];
            if (objective != null && seen.add(objective)) {
                for (ObjectivePacket packet : createStartDisplayingPackets(objective)) {
                    packet.send(link);
                }
            }
        }
    }

    // --- the broadcast helpers ---------------------------------------------------

    private void broadcastObjective(ScoreboardObjective objective, int action) {
        ClientLink.ScoreboardObjectiveData data = objectiveData(objective);
        for (PlayerSession session : players.all()) {
            session.link().scoreboardObjective(data, action);
        }
    }

    private void broadcastScore(String owner, int mode, String objective, int value) {
        for (PlayerSession session : players.all()) {
            session.link().scoreboardScore(owner, mode, objective, value);
        }
    }

    private void broadcastDisplay(int slot, ScoreboardObjective objective) {
        String name = objective == null ? "" : objective.name();
        for (PlayerSession session : players.all()) {
            session.link().scoreboardDisplay(slot, name);
        }
    }

    private void broadcastTeam(ScoreboardTeam team, int action) {
        ClientLink.ScoreboardTeamData data = teamData(team);
        for (PlayerSession session : players.all()) {
            session.link().scoreboardTeam(data, action, List.copyOf(team.members()));
        }
    }

    private void broadcastTeamMembers(ScoreboardTeam team, List<String> members, int action) {
        ClientLink.ScoreboardTeamData data = teamData(team);
        for (PlayerSession session : players.all()) {
            session.link().scoreboardTeam(data, action, members);
        }
    }

    private static ClientLink.ScoreboardObjectiveData objectiveData(ScoreboardObjective o) {
        return new ClientLink.ScoreboardObjectiveData(o.name(), o.displayName(),
                o.criterion().renderType().key());
    }

    private static ClientLink.ScoreboardTeamData teamData(ScoreboardTeam team) {
        return new ClientLink.ScoreboardTeamData(team.name(), team.displayName(),
                team.prefix(), team.suffix(), team.packFriendlyFlags(),
                team.nameTagVisibility().key(), team.colorId());
    }

    // --- the display slot names (Scoreboard's own mapping) ------------------------

    /** The slot's command name (list / sidebar / belowName / sidebar.team.&lt;color&gt;). */
    public static String displayLocation(int slot) {
        switch (slot) {
            case 0:
                return "list";
            case 1:
                return "sidebar";
            case 2:
                return "belowName";
            default:
                if (slot >= 3 && slot <= 18) {
                    return "sidebar.team." + ScoreboardCriterion.COLORS[slot - 3];
                }
                return null;
        }
    }

    /** The slot id of a command name (-1 = unknown, the reference's own answer). */
    public static int displaySlot(String location) {
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
            String color = location.substring("sidebar.team.".length());
            for (int i = 0; i < ScoreboardCriterion.COLORS.length; i++) {
                if (ScoreboardCriterion.COLORS[i].equals(color)) {
                    return i + 3;
                }
            }
        }
        return -1;
    }

    /** One deferred wire write of the start/stop display walks (the packet set's own cell). */
    private record ObjectivePacket(ScoreboardObjective objective, int action, int slot,
                                   ScoreboardScore score) {

        ObjectivePacket(ScoreboardObjective objective, int action) {
            this(objective, action, -1, null);
        }

        /** A slot pointer (objective null = cleared). */
        ObjectivePacket(int slot, ScoreboardObjective objective) {
            this(objective, -1, slot, null);
        }

        ObjectivePacket(ScoreboardScore score) {
            this(score.objective(), -1, -1, score);
        }

        void send(ClientLink link) {
            if (action >= 0) {
                link.scoreboardObjective(new ClientLink.ScoreboardObjectiveData(
                        objective.name(), objective.displayName(),
                        objective.criterion().renderType().key()), action);
            } else if (slot >= 0) {
                link.scoreboardDisplay(slot, objective == null ? "" : objective.name());
            } else {
                link.scoreboardScore(score.owner(), 0, score.objective().name(), score.get());
            }
        }
    }
}
