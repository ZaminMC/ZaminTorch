package net.minecraft.server.command.source;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.EntityNotFoundException;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class CommandResults {
    private static final int TYPE_COUNT = CommandResults.Type.values().length;
    private static final String[] EMPTY = new String[TYPE_COUNT];
    private String[] lastSourceByType = EMPTY;
    private String[] lastObjectiveByType = EMPTY;

    public void add(CommandSource source, CommandResults.Type type, int result) {
        String s = this.lastSourceByType[type.getId()];
        if (s != null) {
            CommandSource commandsource = new CommandSource() {
                @Override
                public String getName() {
                    return source.getName();
                }

                @Override
                public Text getDisplayName() {
                    return source.getDisplayName();
                }

                @Override
                public void sendMessage(Text message) {
                    source.sendMessage(message);
                }

                @Override
                public boolean canUseCommand(int permissionLevel, String command) {
                    return true;
                }

                @Override
                public BlockPos getCommandSourceBlockPos() {
                    return source.getCommandSourceBlockPos();
                }

                @Override
                public Vec3d getCommandSourcePos() {
                    return source.getCommandSourcePos();
                }

                @Override
                public World getCommandSourceWorld() {
                    return source.getCommandSourceWorld();
                }

                @Override
                public Entity asEntity() {
                    return source.asEntity();
                }

                @Override
                public boolean sendCommandSuccessToOps() {
                    return source.sendCommandSuccessToOps();
                }

                @Override
                public void addResult(CommandResults.Type type, int resultx) {
                    source.addResult(type, resultx);
                }
            };

            String s1;
            try {
                s1 = AbstractCommand.parseEntityName(commandsource, s);
            } catch (EntityNotFoundException entitynotfoundexception) {
                return;
            }

            String s2 = this.lastObjectiveByType[type.getId()];
            if (s2 != null) {
                Scoreboard scoreboard = source.getCommandSourceWorld().getScoreboard();
                ScoreboardObjective scoreboardobjective = scoreboard.getObjective(s2);
                if (scoreboardobjective != null) {
                    if (scoreboard.hasScore(s1, scoreboardobjective)) {
                        ScoreboardScore scoreboardscore = scoreboard.getScore(s1, scoreboardobjective);
                        scoreboardscore.set(result);
                    }
                }
            }
        }
    }

    public void readNbt(NbtCompound nbt) {
        if (nbt.contains("CommandStats", 10)) {
            NbtCompound nbtcompound = nbt.getCompound("CommandStats");

            for (CommandResults.Type commandresults$type : CommandResults.Type.values()) {
                String s = commandresults$type.getName() + "Name";
                String s1 = commandresults$type.getName() + "Objective";
                if (nbtcompound.contains(s, 8) && nbtcompound.contains(s1, 8)) {
                    String s2 = nbtcompound.getString(s);
                    String s3 = nbtcompound.getString(s1);
                    updateSourceAndObjective(this, commandresults$type, s2, s3);
                }
            }
        }
    }

    public void writeNbt(NbtCompound nbt) {
        NbtCompound nbtcompound = new NbtCompound();

        for (CommandResults.Type commandresults$type : CommandResults.Type.values()) {
            String s = this.lastSourceByType[commandresults$type.getId()];
            String s1 = this.lastObjectiveByType[commandresults$type.getId()];
            if (s != null && s1 != null) {
                nbtcompound.putString(commandresults$type.getName() + "Name", s);
                nbtcompound.putString(commandresults$type.getName() + "Objective", s1);
            }
        }

        if (!nbtcompound.isEmpty()) {
            nbt.put("CommandStats", nbtcompound);
        }
    }

    public static void updateSourceAndObjective(CommandResults results, CommandResults.Type type, String source, String objective) {
        if (source != null && source.length() != 0 && objective != null && objective.length() != 0) {
            if (results.lastSourceByType == EMPTY || results.lastObjectiveByType == EMPTY) {
                results.lastSourceByType = new String[TYPE_COUNT];
                results.lastObjectiveByType = new String[TYPE_COUNT];
            }

            results.lastSourceByType[type.getId()] = source;
            results.lastObjectiveByType[type.getId()] = objective;
        } else {
            clearSourceAndObjective(results, type);
        }
    }

    private static void clearSourceAndObjective(CommandResults results, CommandResults.Type type) {
        if (results.lastSourceByType != EMPTY && results.lastObjectiveByType != EMPTY) {
            results.lastSourceByType[type.getId()] = null;
            results.lastObjectiveByType[type.getId()] = null;
            boolean flag = true;

            for (CommandResults.Type commandresults$type : CommandResults.Type.values()) {
                if (results.lastSourceByType[commandresults$type.getId()] != null && results.lastObjectiveByType[commandresults$type.getId()] != null) {
                    flag = false;
                    break;
                }
            }

            if (flag) {
                results.lastSourceByType = EMPTY;
                results.lastObjectiveByType = EMPTY;
            }
        }
    }

    public void copy(CommandResults results) {
        for (CommandResults.Type commandresults$type : CommandResults.Type.values()) {
            updateSourceAndObjective(
                this, commandresults$type, results.lastSourceByType[commandresults$type.getId()], results.lastObjectiveByType[commandresults$type.getId()]
            );
        }
    }

    public enum Type {
        SUCCESS_COUNT(0, "SuccessCount"),
        AFFECTED_BLOCKS(1, "AffectedBlocks"),
        AFFECTED_ENTITIES(2, "AffectedEntities"),
        AFFECTED_ITEMS(3, "AffectedItems"),
        QUERY_RESULT(4, "QueryResult");

        final int id;
        final String name;

        Type(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public static String[] getNames() {
            String[] astring = new String[values().length];
            int i = 0;

            for (CommandResults.Type commandresults$type : values()) {
                astring[i++] = commandresults$type.getName();
            }

            return astring;
        }

        public static CommandResults.Type byName(String name) {
            for (CommandResults.Type commandresults$type : values()) {
                if (commandresults$type.getName().equals(name)) {
                    return commandresults$type;
                }
            }

            return null;
        }
    }
}
