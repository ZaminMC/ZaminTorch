package net.zaminmc.torch.server.command;

import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.player.PlayerRegistry;
import net.zaminmc.torch.server.scoreboard.EngineScoreboard;
import net.zaminmc.torch.server.scoreboard.ScoreboardObjective;

/**
 * The command-stat bindings (the reference's CommandResults 1:1): the
 * five result types, each optionally bound to a (source name, objective)
 * pair on the main board. When the holder's command reports a result, the
 * bound owner's score on the bound objective is SET to it — an existing
 * score only, never a created one (the reference's own hasScore gate).
 *
 * <p>Holders live on every entity (players and mobs alike, the reference's
 * Entity base field), on command-block tiles, and on signs. The engine's
 * resolve seam (the players registry + the main board) installs at boot
 * like the selector's own.</p>
 */
public final class EngineCommandResults {

    /** The reference's CommandResults.Type 1:1. */
    public enum Type {
        SUCCESS_COUNT(0, "SuccessCount"),
        AFFECTED_BLOCKS(1, "AffectedBlocks"),
        AFFECTED_ENTITIES(2, "AffectedEntities"),
        AFFECTED_ITEMS(3, "AffectedItems"),
        QUERY_RESULT(4, "QueryResult");

        final int id;
        final String wireName;

        Type(int id, String wireName) {
            this.id = id;
            this.wireName = wireName;
        }

        public int getId() {
            return id;
        }

        /** The reference's own name (the /stats literal + the NBT key stem). */
        public String getName() {
            return wireName;
        }

        /** The reference's byName: the exact-name match, null when unknown. */
        public static Type byName(String name) {
            for (Type type : values()) {
                if (type.wireName.equals(name)) {
                    return type;
                }
            }
            return null;
        }

        public static String[] getNames() {
            String[] names = new String[values().length];
            int i = 0;
            for (Type type : values()) {
                names[i++] = type.wireName;
            }
            return names;
        }
    }

    private static final int TYPE_COUNT = Type.values().length;
    private static final String[] EMPTY = new String[TYPE_COUNT];

    // The resolve seam (the engine installs the registry + the board at boot).
    private static volatile PlayerRegistry players;
    private static volatile EngineScoreboard board;

    /** The boot install (the same seam pattern the selector rides). */
    public static void install(PlayerRegistry players, EngineScoreboard board) {
        EngineCommandResults.players = players;
        EngineCommandResults.board = board;
    }

    private String[] lastSourceByType = EMPTY;
    private String[] lastObjectiveByType = EMPTY;

    /**
     * The reference's add(): the bound owner's score set to the result.
     * Unbound types, unresolvable owners, and owners without an existing
     * score are silent no-ops (the reference's own swallow + hasScore
     * gate); the owner resolves through the parseEntityName ladder against
     * the CURRENT universe.
     */
    public void add(CommandSender source, Type type, int result) {
        String boundSource = lastSourceByType[type.getId()];
        if (boundSource == null) {
            return;
        }
        String owner = resolveEntityName(source, boundSource);
        if (owner == null) {
            return; // the reference's EntityNotFound swallow
        }
        String objectiveName = lastObjectiveByType[type.getId()];
        if (objectiveName == null) {
            return;
        }
        EngineScoreboard scoreboard = board;
        if (scoreboard == null) {
            return;
        }
        ScoreboardObjective objective = scoreboard.getObjective(objectiveName);
        if (objective != null && scoreboard.hasScore(owner, objective)) {
            scoreboard.getScore(owner, objective).set(result);
        }
    }

    /**
     * The reference's parseEntityName ladder (AbstractCommand lines
     * 213-228): a player answers its name; any other entity answers its
     * id (the engine's mobs carry no public uuid — the name key rides, the
     * selector's own ledgered note); an unmatched selector refuses; a
     * plain unmatched string answers itself (a fake owner key sticks).
     */
    private static String resolveEntityName(CommandSender source, String s) {
        try {
            return CommandParse.parsePlayer(players, source, s).name();
        } catch (RuntimeException playerNotFound) {
            try {
                return CommandParse.parseEntity(players, source, s).name();
            } catch (RuntimeException entityNotFound) {
                if (TargetSelector.isValid(s)) {
                    return null;
                }
                return s;
            }
        }
    }

    /**
     * The /stats set arm (the reference's updateSourceAndObjective): a
     * non-empty pair binds, anything else clears.
     */
    public void set(Type type, String sourceName, String objectiveName) {
        if (sourceName != null && !sourceName.isEmpty()
                && objectiveName != null && !objectiveName.isEmpty()) {
            if (lastSourceByType == EMPTY || lastObjectiveByType == EMPTY) {
                lastSourceByType = new String[TYPE_COUNT];
                lastObjectiveByType = new String[TYPE_COUNT];
            }
            lastSourceByType[type.getId()] = sourceName;
            lastObjectiveByType[type.getId()] = objectiveName;
        } else {
            clear(type);
        }
    }

    /** The /stats clear arm (the reference's clearSourceAndObjective). */
    public void clear(Type type) {
        if (lastSourceByType != EMPTY && lastObjectiveByType != EMPTY) {
            lastSourceByType[type.getId()] = null;
            lastObjectiveByType[type.getId()] = null;
            boolean allEmpty = true;
            for (Type t : Type.values()) {
                if (lastSourceByType[t.getId()] != null && lastObjectiveByType[t.getId()] != null) {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty) {
                lastSourceByType = EMPTY;
                lastObjectiveByType = EMPTY;
            }
        }
    }

    /** @return the bound source name of the type, or null (the read surface). */
    public String sourceOf(Type type) {
        return lastSourceByType == EMPTY ? null : lastSourceByType[type.getId()];
    }

    /** @return the bound objective name of the type, or null (the read surface). */
    public String objectiveOf(Type type) {
        return lastObjectiveByType == EMPTY ? null : lastObjectiveByType[type.getId()];
    }

    /** The persistence gate (the reference's writeNbt !isEmpty check). */
    public boolean hasBindings() {
        for (Type type : Type.values()) {
            if (lastSourceByType != EMPTY
                    && lastSourceByType[type.getId()] != null
                    && lastObjectiveByType != EMPTY
                    && lastObjectiveByType[type.getId()] != null) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The NBT projections (Slice 89 — the reference's CommandResults
    // readNbt/writeNbt, 1:1)
    // ------------------------------------------------------------------

    /**
     * The reference's readNbt: the CommandStats compound's per-type
     * {@code <Name>Name/<Name>Objective} STRING pairs, each binding through
     * the same set gate as /stats.
     */
    public void readNbt(java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> nbt) {
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats =
                net.zaminmc.torch.server.nbt.NbtOps.getCompound(nbt, "CommandStats");
        if (stats.isEmpty()) {
            return;
        }
        for (Type type : Type.values()) {
            String nameKey = type.getName() + "Name";
            String objectiveKey = type.getName() + "Objective";
            if (net.zaminmc.torch.server.nbt.NbtOps.contains(stats, nameKey,
                            net.zaminmc.torch.server.nbt.Snbt.Tag.Type.STRING)
                    && net.zaminmc.torch.server.nbt.NbtOps.contains(stats, objectiveKey,
                            net.zaminmc.torch.server.nbt.Snbt.Tag.Type.STRING)) {
                set(type,
                        net.zaminmc.torch.server.nbt.NbtOps.getString(stats, nameKey),
                        net.zaminmc.torch.server.nbt.NbtOps.getString(stats, objectiveKey));
            }
        }
    }

    /**
     * The reference's writeNbt: every bound type's pair as STRING rows,
     * the whole compound only when at least one pair exists.
     */
    public void writeNbt(java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> nbt) {
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats =
                new java.util.LinkedHashMap<>();
        for (Type type : Type.values()) {
            String source = sourceOf(type);
            String objective = objectiveOf(type);
            if (source != null && objective != null) {
                net.zaminmc.torch.server.nbt.NbtOps.putString(stats,
                        type.getName() + "Name", source);
                net.zaminmc.torch.server.nbt.NbtOps.putString(stats,
                        type.getName() + "Objective", objective);
            }
        }
        if (!stats.isEmpty()) {
            net.zaminmc.torch.server.nbt.NbtOps.putCompound(nbt, "CommandStats", stats);
        }
    }
}
