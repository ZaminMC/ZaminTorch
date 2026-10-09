package net.minecraft.server.scoreboard;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.scoreboard.SavedScoreboardData;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;

public class ServerScoreboard extends Scoreboard {
    private final MinecraftServer server;
    private final Set<ScoreboardObjective> displayedObjectives = Sets.newHashSet();
    private SavedScoreboardData savedData;

    public ServerScoreboard(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void onScoreUpdated(ScoreboardScore score) {
        super.onScoreUpdated(score);
        if (this.displayedObjectives.contains(score.getObjective())) {
            this.server.getPlayerManager().sendPacket(new ScoreboardScoreS2CPacket(score));
        }

        this.markDirty();
    }

    @Override
    public void onScoresRemoved(String owner) {
        super.onScoresRemoved(owner);
        this.server.getPlayerManager().sendPacket(new ScoreboardScoreS2CPacket(owner));
        this.markDirty();
    }

    @Override
    public void onScoreRemoved(String owner, ScoreboardObjective objective) {
        super.onScoreRemoved(owner, objective);
        this.server.getPlayerManager().sendPacket(new ScoreboardScoreS2CPacket(owner, objective));
        this.markDirty();
    }

    @Override
    public void setDisplayObjective(int slot, ScoreboardObjective objective) {
        ScoreboardObjective scoreboardobjective = this.getDisplayObjective(slot);
        super.setDisplayObjective(slot, objective);
        if (scoreboardobjective != objective && scoreboardobjective != null) {
            if (this.getDisplaySlot(scoreboardobjective) > 0) {
                this.server.getPlayerManager().sendPacket(new ScoreboardDisplayS2CPacket(slot, objective));
            } else {
                this.stopDisplayingObjective(scoreboardobjective);
            }
        }

        if (objective != null) {
            if (this.displayedObjectives.contains(objective)) {
                this.server.getPlayerManager().sendPacket(new ScoreboardDisplayS2CPacket(slot, objective));
            } else {
                this.startDisplayingObjective(objective);
            }
        }

        this.markDirty();
    }

    @Override
    public boolean addMemberToTeam(String member, String teamName) {
        if (super.addMemberToTeam(member, teamName)) {
            Team team = this.getTeam(teamName);
            this.server.getPlayerManager().sendPacket(new TeamS2CPacket(team, Arrays.asList(member), 3));
            this.markDirty();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void removeMemberFromTeam(String member, Team team) {
        super.removeMemberFromTeam(member, team);
        this.server.getPlayerManager().sendPacket(new TeamS2CPacket(team, Arrays.asList(member), 4));
        this.markDirty();
    }

    @Override
    public void onObjectiveCreated(ScoreboardObjective objective) {
        super.onObjectiveCreated(objective);
        this.markDirty();
    }

    @Override
    public void onObjectiveUpdated(ScoreboardObjective objective) {
        super.onObjectiveUpdated(objective);
        if (this.displayedObjectives.contains(objective)) {
            this.server.getPlayerManager().sendPacket(new ScoreboardObjectiveS2CPacket(objective, 2));
        }

        this.markDirty();
    }

    @Override
    public void onObjectiveRemoved(ScoreboardObjective objective) {
        super.onObjectiveRemoved(objective);
        if (this.displayedObjectives.contains(objective)) {
            this.stopDisplayingObjective(objective);
        }

        this.markDirty();
    }

    @Override
    public void onTeamAdded(Team team) {
        super.onTeamAdded(team);
        this.server.getPlayerManager().sendPacket(new TeamS2CPacket(team, 0));
        this.markDirty();
    }

    @Override
    public void onTeamUpdated(Team team) {
        super.onTeamUpdated(team);
        this.server.getPlayerManager().sendPacket(new TeamS2CPacket(team, 2));
        this.markDirty();
    }

    @Override
    public void onTeamRemoved(Team team) {
        super.onTeamRemoved(team);
        this.server.getPlayerManager().sendPacket(new TeamS2CPacket(team, 1));
        this.markDirty();
    }

    public void setSavedData(SavedScoreboardData savedData) {
        this.savedData = savedData;
    }

    protected void markDirty() {
        if (this.savedData != null) {
            this.savedData.markDirty();
        }
    }

    public List<Packet> createStartDisplayingObjectivePackets(ScoreboardObjective objective) {
        List<Packet> list = Lists.newArrayList();
        list.add(new ScoreboardObjectiveS2CPacket(objective, 0));

        for (int i = 0; i < 19; i++) {
            if (this.getDisplayObjective(i) == objective) {
                list.add(new ScoreboardDisplayS2CPacket(i, objective));
            }
        }

        for (ScoreboardScore scoreboardscore : this.getScores(objective)) {
            list.add(new ScoreboardScoreS2CPacket(scoreboardscore));
        }

        return list;
    }

    public void startDisplayingObjective(ScoreboardObjective objective) {
        List<Packet> list = this.createStartDisplayingObjectivePackets(objective);

        for (ServerPlayerEntity serverplayerentity : this.server.getPlayerManager().getAll()) {
            for (Packet packet : list) {
                serverplayerentity.networkHandler.sendPacket(packet);
            }
        }

        this.displayedObjectives.add(objective);
    }

    public List<Packet> createStopDisplayingObjectivePackets(ScoreboardObjective objective) {
        List<Packet> list = Lists.newArrayList();
        list.add(new ScoreboardObjectiveS2CPacket(objective, 1));

        for (int i = 0; i < 19; i++) {
            if (this.getDisplayObjective(i) == objective) {
                list.add(new ScoreboardDisplayS2CPacket(i, objective));
            }
        }

        return list;
    }

    public void stopDisplayingObjective(ScoreboardObjective objective) {
        List<Packet> list = this.createStopDisplayingObjectivePackets(objective);

        for (ServerPlayerEntity serverplayerentity : this.server.getPlayerManager().getAll()) {
            for (Packet packet : list) {
                serverplayerentity.networkHandler.sendPacket(packet);
            }
        }

        this.displayedObjectives.remove(objective);
    }

    public int getDisplaySlot(ScoreboardObjective objective) {
        int i = 0;

        for (int j = 0; j < 19; j++) {
            if (this.getDisplayObjective(j) == objective) {
                i++;
            }
        }

        return i;
    }
}
