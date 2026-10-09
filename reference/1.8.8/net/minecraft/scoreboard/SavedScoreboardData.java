package net.minecraft.scoreboard;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.Formatting;
import net.minecraft.world.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SavedScoreboardData extends SavedData {
    private static final Logger LOGGER = LogManager.getLogger();
    private Scoreboard scoreboard;
    private NbtCompound nbt;

    public SavedScoreboardData() {
        this("scoreboard");
    }

    public SavedScoreboardData(String string) {
        super(string);
    }

    public void setScoreboard(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
        if (this.nbt != null) {
            this.readNbt(this.nbt);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        if (this.scoreboard == null) {
            this.nbt = nbt;
        } else {
            this.readObjectivesFromNbt(nbt.getList("Objectives", 10));
            this.readScoresFromNbt(nbt.getList("PlayerScores", 10));
            if (nbt.contains("DisplaySlots", 10)) {
                this.readDisplaySlotsFromNbt(nbt.getCompound("DisplaySlots"));
            }

            if (nbt.contains("Teams", 9)) {
                this.readTeamsFromNbt(nbt.getList("Teams", 10));
            }
        }
    }

    protected void readTeamsFromNbt(NbtList nbt) {
        for (int i = 0; i < nbt.size(); i++) {
            NbtCompound nbtcompound = nbt.getCompound(i);
            String s = nbtcompound.getString("Name");
            if (s.length() > 16) {
                s = s.substring(0, 16);
            }

            Team team = this.scoreboard.addTeam(s);
            String s1 = nbtcompound.getString("DisplayName");
            if (s1.length() > 32) {
                s1 = s1.substring(0, 32);
            }

            team.setDisplayName(s1);
            if (nbtcompound.contains("TeamColor", 8)) {
                team.setColor(Formatting.byName(nbtcompound.getString("TeamColor")));
            }

            team.setPrefix(nbtcompound.getString("Prefix"));
            team.setSuffix(nbtcompound.getString("Suffix"));
            if (nbtcompound.contains("AllowFriendlyFire", 99)) {
                team.setAllowFriendlyFire(nbtcompound.getBoolean("AllowFriendlyFire"));
            }

            if (nbtcompound.contains("SeeFriendlyInvisibles", 99)) {
                team.setShowFriendlyInvisibles(nbtcompound.getBoolean("SeeFriendlyInvisibles"));
            }

            if (nbtcompound.contains("NameTagVisibility", 8)) {
                AbstractTeam.Visibility abstractteam$visibility = AbstractTeam.Visibility.byKey(nbtcompound.getString("NameTagVisibility"));
                if (abstractteam$visibility != null) {
                    team.setNameTagVisibility(abstractteam$visibility);
                }
            }

            if (nbtcompound.contains("DeathMessageVisibility", 8)) {
                AbstractTeam.Visibility abstractteam$visibility1 = AbstractTeam.Visibility.byKey(nbtcompound.getString("DeathMessageVisibility"));
                if (abstractteam$visibility1 != null) {
                    team.setDeathMessageVisibility(abstractteam$visibility1);
                }
            }

            this.readTeamMembersFromNbt(team, nbtcompound.getList("Players", 8));
        }
    }

    protected void readTeamMembersFromNbt(Team team, NbtList nbt) {
        for (int i = 0; i < nbt.size(); i++) {
            this.scoreboard.addMemberToTeam(nbt.getString(i), team.getName());
        }
    }

    protected void readDisplaySlotsFromNbt(NbtCompound nbt) {
        for (int i = 0; i < 19; i++) {
            if (nbt.contains("slot_" + i, 8)) {
                String s = nbt.getString("slot_" + i);
                ScoreboardObjective scoreboardobjective = this.scoreboard.getObjective(s);
                this.scoreboard.setDisplayObjective(i, scoreboardobjective);
            }
        }
    }

    protected void readObjectivesFromNbt(NbtList nbt) {
        for (int i = 0; i < nbt.size(); i++) {
            NbtCompound nbtcompound = nbt.getCompound(i);
            ScoreboardCriterion scoreboardcriterion = ScoreboardCriterion.BY_NAME.get(nbtcompound.getString("CriteriaName"));
            if (scoreboardcriterion != null) {
                String s = nbtcompound.getString("Name");
                if (s.length() > 16) {
                    s = s.substring(0, 16);
                }

                ScoreboardObjective scoreboardobjective = this.scoreboard.createObjective(s, scoreboardcriterion);
                scoreboardobjective.setDisplayName(nbtcompound.getString("DisplayName"));
                scoreboardobjective.setRenderType(ScoreboardCriterion.RenderType.byKey(nbtcompound.getString("RenderType")));
            }
        }
    }

    protected void readScoresFromNbt(NbtList nbt) {
        for (int i = 0; i < nbt.size(); i++) {
            NbtCompound nbtcompound = nbt.getCompound(i);
            ScoreboardObjective scoreboardobjective = this.scoreboard.getObjective(nbtcompound.getString("Objective"));
            String s = nbtcompound.getString("Name");
            if (s.length() > 40) {
                s = s.substring(0, 40);
            }

            ScoreboardScore scoreboardscore = this.scoreboard.getScore(s, scoreboardobjective);
            scoreboardscore.set(nbtcompound.getInt("Score"));
            if (nbtcompound.contains("Locked")) {
                scoreboardscore.setLocked(nbtcompound.getBoolean("Locked"));
            }
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        if (this.scoreboard == null) {
            LOGGER.warn("Tried to save scoreboard without having a scoreboard...");
        } else {
            nbt.put("Objectives", this.objectivesToNbt());
            nbt.put("PlayerScores", this.scoresToNbt());
            nbt.put("Teams", this.teamsToNbt());
            this.writeDisplaySlots(nbt);
        }
    }

    protected NbtList teamsToNbt() {
        NbtList nbtlist = new NbtList();

        for (Team team : this.scoreboard.getTeams()) {
            NbtCompound nbtcompound = new NbtCompound();
            nbtcompound.putString("Name", team.getName());
            nbtcompound.putString("DisplayName", team.getDisplayName());
            if (team.getColor().getId() >= 0) {
                nbtcompound.putString("TeamColor", team.getColor().getName());
            }

            nbtcompound.putString("Prefix", team.getPrefix());
            nbtcompound.putString("Suffix", team.getSuffix());
            nbtcompound.putBoolean("AllowFriendlyFire", team.allowFriendlyFire());
            nbtcompound.putBoolean("SeeFriendlyInvisibles", team.showFriendlyInvisibles());
            nbtcompound.putString("NameTagVisibility", team.getNameTagVisibility().key);
            nbtcompound.putString("DeathMessageVisibility", team.getDeathMessageVisibility().key);
            NbtList nbtlist1 = new NbtList();

            for (String s : team.getMembers()) {
                nbtlist1.addElement(new NbtString(s));
            }

            nbtcompound.put("Players", nbtlist1);
            nbtlist.addElement(nbtcompound);
        }

        return nbtlist;
    }

    protected void writeDisplaySlots(NbtCompound nbt) {
        NbtCompound nbtcompound = new NbtCompound();
        boolean flag = false;

        for (int i = 0; i < 19; i++) {
            ScoreboardObjective scoreboardobjective = this.scoreboard.getDisplayObjective(i);
            if (scoreboardobjective != null) {
                nbtcompound.putString("slot_" + i, scoreboardobjective.getName());
                flag = true;
            }
        }

        if (flag) {
            nbt.put("DisplaySlots", nbtcompound);
        }
    }

    protected NbtList objectivesToNbt() {
        NbtList nbtlist = new NbtList();

        for (ScoreboardObjective scoreboardobjective : this.scoreboard.getObjectives()) {
            if (scoreboardobjective.getCriterion() != null) {
                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putString("Name", scoreboardobjective.getName());
                nbtcompound.putString("CriteriaName", scoreboardobjective.getCriterion().getName());
                nbtcompound.putString("DisplayName", scoreboardobjective.getDisplayName());
                nbtcompound.putString("RenderType", scoreboardobjective.getRenderType().getKey());
                nbtlist.addElement(nbtcompound);
            }
        }

        return nbtlist;
    }

    protected NbtList scoresToNbt() {
        NbtList nbtlist = new NbtList();

        for (ScoreboardScore scoreboardscore : this.scoreboard.getScores()) {
            if (scoreboardscore.getObjective() != null) {
                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putString("Name", scoreboardscore.getOwner());
                nbtcompound.putString("Objective", scoreboardscore.getObjective().getName());
                nbtcompound.putInt("Score", scoreboardscore.get());
                nbtcompound.putBoolean("Locked", scoreboardscore.isLocked());
                nbtlist.addElement(nbtcompound);
            }
        }

        return nbtlist;
    }
}
