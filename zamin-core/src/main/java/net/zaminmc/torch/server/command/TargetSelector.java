package net.zaminmc.torch.server.command;

import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.scoreboard.EngineScoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Predicate;

/**
 * The 1.8.8 command target selector (TargetSelector.java 1:1) over the
 * engine's own candidate universe — the whole missing-commands batch's
 * shared dependency (/title's parsePlayer, tellraw's selector components,
 * testfor, execute, kill @e, spreadplayers, stats).
 *
 * <p>The reference's {@code <T extends Entity>} walk becomes the engine's
 * {@link Candidate} record; every predicate family (type, xp, gamemode,
 * team, score_, name, rm/r, ry/rx, c) ports with the reference's own
 * defaults and quirks — the wrapDegrees windows, the r shuffle, the p/a/e
 * distance sort, the self-bias when {@code c==1} and the source's own body
 * is in the match, the dx/dy/dz box with the negative-extension walk, and
 * the positional-args gate that pins the world to the source's own
 * dimension.</p>
 *
 * <p>Known engine adaptations (ledgered): the default universe is the
 * players + the living mobs (the other entity kinds arrive through the
 * {@link Universe} seam — additive, no walk changes); the engine's mobs
 * carry no public uuid, so the score predicate's non-player owner key
 * rides the mob's name; the mobs' pitch reads 0 (the engine tracks yaw);
 * and the reference's getPlayers/getEntities optimization branches
 * collapse into the single full-universe scan (the same result set, the
 * engine's candidate lists are already point-in-time copies).</p>
 */
public final class TargetSelector {

    private TargetSelector() {
    }

    // ------------------------------------------------------------------ seam

    /** One selector candidate: a player or a living mob of some dimension. */
    public record Candidate(
            Object handle,
            boolean player,
            String name,
            java.util.UUID uuid,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            int dimension) {

        public static Candidate candidateOf(PlayerSession session) {
            return new Candidate(
                    session,
                    true,
                    session.name(),
                    session.uuid(),
                    session.position().x(),
                    session.position().y(),
                    session.position().z(),
                    session.rotation().yaw(),
                    session.rotation().pitch(),
                    session.dimension());
        }

        public static Candidate candidateOf(net.zaminmc.torch.server.entity.MobEntity mob) {
            String name = mob.customName() != null ? mob.customName() : mob.type().vanillaName();
            return new Candidate(
                    mob,
                    false,
                    name,
                    null, // the engine's mobs carry no public uuid (ledgered)
                    mob.position().x(),
                    mob.position().y(),
                    mob.position().z(),
                    mob.yaw(),
                    0.0f, // the engine tracks yaw only (ledgered)
                    mob.dimension());
        }
    }

    /** The per-dimension candidate universe (the engine installs one at boot). */
    public interface Universe {
        List<Candidate> candidatesOf(int dimension);
    }

    /** The source-side view the walk needs — the engine's CommandSource. */
    public interface SourceView {
        /** @return the source's block position (players: the floored body; console: origin). */
        int[] blockPos();

        /** @return the source's dimension id (console: 0 — the overworld). */
        int dimension();

        /** @return the sending player's candidate, or null for the console. */
        Candidate asEntity();

        /** The permission gate (the reference's canUseCommand(1, "@")). */
        boolean canUseSelectors();

        /** Delivers the invalid-type feedback line (the reference's red translatable). */
        void sendInvalidType(String typeName);
    }

    /** The selector universe (installed by the engine at boot). */
    private static volatile Universe universe;

    /** The main-board consult (score_ predicates and team lookups). */
    private static volatile java.util.function.Supplier<EngineScoreboard> boardSupplier;

    public static void install(Universe u, java.util.function.Supplier<EngineScoreboard> board) {
        universe = u;
        boardSupplier = board;
    }

    // ------------------------------------------------------------------ patterns

    /** The reference's own three patterns, verbatim. */
    private static final Pattern TARGET_SELECTOR_PATTERN = Pattern.compile("^@([pare])(?:\\[([\\w=,!-]*)\\])?$");
    private static final Pattern VALUE_PATTERN = Pattern.compile("\\G([-!]?[\\w-]*)(?:$|,)");
    private static final Pattern ARG_PATTERN = Pattern.compile("\\G(\\w+)=([-!]?[\\w-]*)(?:$|,)");
    private static final java.util.Set<String> POS_ARGS =
            java.util.Set.of("x", "y", "z", "dx", "dy", "dz", "rm", "r");

    // ------------------------------------------------------------------ entry points

    /** The reference's selectFirstPlayer: exactly one match, else null. */
    public static PlayerSession selectFirstPlayer(SourceView source, String s) {
        List<Candidate> list = select(source, s);
        return list.size() == 1 && list.get(0).player() ? (PlayerSession) list.get(0).handle() : null;
    }

    /** The reference's selectFirst: exactly one match of any kind, else null. */
    public static Candidate selectFirst(SourceView source, String s) {
        List<Candidate> list = select(source, s);
        return list.size() == 1 ? list.get(0) : null;
    }

    /** The reference's getSelectionAsText: the matched display names (the listText walk's input). */
    public static List<String> getSelectionNames(SourceView source, String s) {
        List<Candidate> list = select(source, s);
        List<String> names = new ArrayList<>();
        for (Candidate candidate : list) {
            names.add(candidate.name());
        }
        return names;
    }

    /** The reference's select: the full walk. */
    public static List<Candidate> select(SourceView source, String s) {
        Matcher matcher = TARGET_SELECTOR_PATTERN.matcher(s);
        if (!matcher.matches() || !source.canUseSelectors()) {
            return List.of();
        }
        Map<String, String> map = parseArgs(matcher.group(2));
        if (!hasTypeArg(source, map)) {
            return List.of();
        }

        String sx = matcher.group(1);
        int[] blockpos = parsePos(map, source.blockPos());
        List<Integer> dimensions = getDimensions(source, map);
        List<Candidate> list1 = new ArrayList<>();
        for (Integer dimension : dimensions) {
            if (dimension != null) {
                List<Predicate<Candidate>> list2 = new ArrayList<>();
                list2.addAll(getTypePredicates(map, sx));
                list2.addAll(getXpPredicates(map));
                list2.addAll(getGameModePredicates(map));
                list2.addAll(getTeamPredicates(map));
                list2.addAll(getScoreboardPredicates(map));
                list2.addAll(getNamePredicates(map));
                list2.addAll(getDistancePredicates(map, blockpos));
                list2.addAll(getRotationPredicates(map));
                list1.addAll(select(map, list2, sx, dimension, blockpos));
            }
        }

        return filterSelection(list1, map, source, sx, blockpos);
    }

    // ------------------------------------------------------------------ the worlds

    /** The reference's getWorlds: a positional arg pins the walk to the source's own dimension. */
    private static List<Integer> getDimensions(SourceView source, Map<String, String> args) {
        List<Integer> list = new ArrayList<>();
        if (hasPosArg(args)) {
            list.add(source.dimension());
        } else {
            // MinecraftServer.getInstance().worlds — the engine's live
            // dimensions, the reference's own ids: 0 the overworld, -1 the
            // nether, 1 the End.
            list.add(0);
            list.add(-1);
            list.add(1);
        }
        return list;
    }

    // ------------------------------------------------------------------ the predicates

    private static boolean hasTypeArg(SourceView source, Map<String, String> args) {
        String s = getArg(args, "type");
        s = s != null && s.startsWith("!") ? s.substring(1) : s;
        if (s != null && !isTypeKey(s)) {
            source.sendInvalidType(s);
            return false;
        }
        return true;
    }

    /** The reference's Entities.isKey — the engine's key set is the mob table's vanilla names. */
    static boolean isTypeKey(String key) {
        for (net.zaminmc.torch.server.entity.MobType type : net.zaminmc.torch.server.entity.MobType.values()) {
            if (type.vanillaName().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static List<Predicate<Candidate>> getTypePredicates(Map<String, String> args, String s) {
        List<Predicate<Candidate>> list = new ArrayList<>();
        String sx = getArg(args, "type");
        boolean flag = sx != null && sx.startsWith("!");
        if (flag) {
            sx = sx.substring(1);
        }
        final String s1 = sx;
        if ((sx == null || !s.equals("e")) && !(s.equals("r") && sx != null)) {
            if (!s.equals("e")) {
                // p/a/r without a type: players only
                list.add(Candidate::player);
            }
        } else {
            // e (or r with a type): the type predicate over every kind
            final boolean negate = flag;
            list.add(candidate -> isOfType(candidate, s1) != negate);
        }
        return list;
    }

    /** The reference's Entities.is — the engine's kind dispatch. */
    private static boolean isOfType(Candidate candidate, String key) {
        if (candidate.player()) {
            return false; // players are not in the entity-type registry
        }
        net.zaminmc.torch.server.entity.MobEntity mob =
                (net.zaminmc.torch.server.entity.MobEntity) candidate.handle();
        return mob.type().vanillaName().equals(key);
    }

    private static List<Predicate<Candidate>> getXpPredicates(Map<String, String> args) {
        List<Predicate<Candidate>> list = new ArrayList<>();
        final int i = parseInt(args, "lm", -1);
        final int j = parseInt(args, "l", -1);
        if (i > -1 || j > -1) {
            list.add(candidate -> {
                if (!candidate.player()) {
                    return false;
                }
                PlayerSession session = (PlayerSession) candidate.handle();
                return (i <= -1 || session.experienceLevel() >= i)
                        && (j <= -1 || session.experienceLevel() <= j);
            });
        }
        return list;
    }

    private static List<Predicate<Candidate>> getGameModePredicates(Map<String, String> args) {
        List<Predicate<Candidate>> list = new ArrayList<>();
        final int i = parseInt(args, "m", -1);
        if (i != -1) {
            list.add(candidate -> {
                if (!candidate.player()) {
                    return false;
                }
                PlayerSession session = (PlayerSession) candidate.handle();
                return session.gamemode().legacyId() == i;
            });
        }
        return list;
    }

    private static List<Predicate<Candidate>> getTeamPredicates(Map<String, String> args) {
        List<Predicate<Candidate>> list = new ArrayList<>();
        String s = getArg(args, "team");
        boolean flag = s != null && s.startsWith("!");
        if (flag) {
            s = s.substring(1);
        }
        final String s1 = s;
        if (s != null) {
            list.add(candidate -> {
                EngineScoreboard board = board();
                if (board == null) {
                    return false;
                }
                var team = board.getTeamOfMember(candidate.name());
                String teamName = team == null ? "" : team.name();
                return teamName.equals(s1) != flag;
            });
        }
        return list;
    }

    private static List<Predicate<Candidate>> getScoreboardPredicates(Map<String, String> args) {
        List<Predicate<Candidate>> list = new ArrayList<>();
        final Map<String, Integer> map = parseScoreboardScores(args);
        if (!map.isEmpty()) {
            list.add(candidate -> {
                EngineScoreboard board = board();
                if (board == null) {
                    return false;
                }
                for (Map.Entry<String, Integer> entry : map.entrySet()) {
                    String s = entry.getKey();
                    boolean flag = false;
                    if (s.endsWith("_min") && s.length() > 4) {
                        flag = true;
                        s = s.substring(0, s.length() - 4);
                    }
                    var objective = board.getObjective(s);
                    if (objective == null) {
                        return false;
                    }
                    // The reference: players key by name, other entities by
                    // uuid — the engine's mobs carry no public uuid (ledgered)
                    String owner = candidate.name();
                    if (!board.hasScore(owner, objective)) {
                        return false;
                    }
                    int i = board.getScore(owner, objective).get();
                    if (i < entry.getValue() && flag) {
                        return false;
                    }
                    if (i > entry.getValue() && !flag) {
                        return false;
                    }
                }
                return true;
            });
        }
        return list;
    }

    private static List<Predicate<Candidate>> getNamePredicates(Map<String, String> args) {
        List<Predicate<Candidate>> list = new ArrayList<>();
        String s = getArg(args, "name");
        boolean flag = s != null && s.startsWith("!");
        if (flag) {
            s = s.substring(1);
        }
        final String s1 = s;
        if (s != null) {
            list.add(candidate -> candidate.name().equals(s1) != flag);
        }
        return list;
    }

    private static List<Predicate<Candidate>> getDistancePredicates(Map<String, String> args, int[] pos) {
        List<Predicate<Candidate>> list = new ArrayList<>();
        final int i = parseInt(args, "rm", -1);
        final int j = parseInt(args, "r", -1);
        if (pos != null && (i >= 0 || j >= 0)) {
            final int k = i * i;
            final int l = j * j;
            list.add(candidate -> {
                int i1 = (int) squaredDistanceToCenter(candidate, pos);
                return (i < 0 || i1 >= k) && (j < 0 || i1 <= l);
            });
        }
        return list;
    }

    private static List<Predicate<Candidate>> getRotationPredicates(Map<String, String> args) {
        List<Predicate<Candidate>> list = new ArrayList<>();
        if (args.containsKey("rym") || args.containsKey("ry")) {
            final int i = wrapDegrees(parseInt(args, "rym", 0));
            final int j = wrapDegrees(parseInt(args, "ry", 359));
            list.add(candidate -> {
                int i1 = wrapDegrees((int) Math.floor(candidate.yaw()));
                return i > j ? i1 >= i || i1 <= j : i1 >= i && i1 <= j;
            });
        }
        if (args.containsKey("rxm") || args.containsKey("rx")) {
            final int k = wrapDegrees(parseInt(args, "rxm", 0));
            final int l = wrapDegrees(parseInt(args, "rx", 359));
            list.add(candidate -> {
                int i1 = wrapDegrees((int) Math.floor(candidate.pitch()));
                return k > l ? i1 >= k || i1 <= l : i1 >= k && i1 <= l;
            });
        }
        return list;
    }

    // ------------------------------------------------------------------ the scan

    private static List<Candidate> select(
            Map<String, String> args, List<Predicate<Candidate>> predicates, String rawTarget, int dimension, int[] pos) {
        List<Candidate> list = new ArrayList<>();
        String s = getArg(args, "type");
        s = s != null && s.startsWith("!") ? s.substring(1) : s;
        int dx = parseInt(args, "dx", 0);
        int dy = parseInt(args, "dy", 0);
        int dz = parseInt(args, "dz", 0);
        int r = parseInt(args, "r", -1);
        Predicate<Candidate> predicate = predicates.stream().reduce(x -> true, Predicate::and);
        Predicate<Candidate> predicate1 = predicate.and(TargetSelector::isAlive);

        Universe u = universe;
        if (u == null) {
            return list;
        }
        List<Candidate> population = u.candidatesOf(dimension);
        if (pos != null) {
            if (args.containsKey("dx") || args.containsKey("dy") || args.containsKey("dz")) {
                double[] box1 = getBox(pos, dx, dy, dz);
                for (Candidate candidate : population) {
                    if (predicate1.test(candidate) && inBox(candidate, box1)) {
                        list.add(candidate);
                    }
                }
            } else if (r >= 0) {
                double[] box = new double[] {
                        pos[0] - r, pos[1] - r, pos[2] - r,
                        pos[0] + r + 1, pos[1] + r + 1, pos[2] + r + 1
                };
                for (Candidate candidate : population) {
                    if (predicate1.test(candidate) && inBox(candidate, box)) {
                        list.add(candidate);
                    }
                }
            } else if (rawTarget.equals("a")) {
                for (Candidate candidate : population) {
                    if (predicate.test(candidate)) {
                        list.add(candidate);
                    }
                }
            } else {
                // p, r, e: the reference's remaining three arms all land in the
                // same full-scan shape here (the engine's collapsed form)
                for (Candidate candidate : population) {
                    if (predicate1.test(candidate)) {
                        list.add(candidate);
                    }
                }
            }
        } else if (rawTarget.equals("a")) {
            for (Candidate candidate : population) {
                if (predicate.test(candidate)) {
                    list.add(candidate);
                }
            }
        } else {
            for (Candidate candidate : population) {
                if (predicate1.test(candidate)) {
                    list.add(candidate);
                }
            }
        }

        return list;
    }

    /** The reference's EntityFilter.ALIVE — the living read. */
    private static boolean isAlive(Candidate candidate) {
        if (candidate.player()) {
            return true;
        }
        return !((net.zaminmc.torch.server.entity.MobEntity) candidate.handle()).dead();
    }

    /** The reference's box containment: min-inclusive, max-exclusive per axis. */
    private static boolean inBox(Candidate candidate, double[] box) {
        return !(candidate.x() < box[0])
                && !(candidate.y() < box[1])
                && !(candidate.z() < box[2])
                && !(candidate.x() >= box[3])
                && !(candidate.y() >= box[4])
                && !(candidate.z() >= box[5]);
    }

    private static List<Candidate> filterSelection(
            List<Candidate> entities, Map<String, String> args, SourceView source, String rawTarget, int[] pos) {
        int i = parseInt(args, "c", !rawTarget.equals("a") && !rawTarget.equals("e") ? 1 : 0);
        if (!rawTarget.equals("p") && !rawTarget.equals("a") && !rawTarget.equals("e")) {
            if (rawTarget.equals("r")) {
                java.util.Collections.shuffle(entities);
            }
        } else if (pos != null) {
            final int[] p = pos;
            entities.sort(Comparator.comparingDouble(c -> squaredDistanceTo(c, p)));
        }

        Candidate entity = source.asEntity();
        if (entity != null && i == 1 && !"r".equals(rawTarget)) {
            // The reference's entities.contains(entity) rides object identity;
            // the engine's record equality would flake on a mid-walk move, so
            // the self-bias matches the HANDLE and keeps the list's own row.
            Candidate self = null;
            for (Candidate candidate : entities) {
                if (candidate.handle() == entity.handle()) {
                    self = candidate;
                    break;
                }
            }
            if (self != null) {
                entities = new ArrayList<>(List.of(self));
            }
        }

        if (i != 0) {
            if (i < 0) {
                java.util.Collections.reverse(entities);
            }
            entities = entities.subList(0, Math.min(Math.abs(i), entities.size()));
        }

        return entities;
    }

    // ------------------------------------------------------------------ helpers

    /** The reference's getBox: the negative-d extension walk. */
    private static double[] getBox(int[] pos, int dx, int dy, int dz) {
        boolean flag = dx < 0;
        boolean flag1 = dy < 0;
        boolean flag2 = dz < 0;
        double d0 = pos[0] + (flag ? dx : 0);
        double d1 = pos[1] + (flag1 ? dy : 0);
        double d2 = pos[2] + (flag2 ? dz : 0);
        double d3 = pos[0] + (flag ? 0 : dx) + 1;
        double d4 = pos[1] + (flag1 ? 0 : dy) + 1;
        double d5 = pos[2] + (flag2 ? 0 : dz) + 1;
        return new double[] {d0, d1, d2, d3, d4, d5};
    }

    /** The reference's getSquaredDistanceToCenter: the block-center distance. */
    private static double squaredDistanceToCenter(Candidate candidate, int[] pos) {
        double d0 = pos[0] + 0.5 - candidate.x();
        double d1 = pos[1] + 0.5 - candidate.y();
        double d2 = pos[2] + 0.5 - candidate.z();
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    /** The reference's getSquaredDistanceTo(BlockPos): the raw-coord distance. */
    private static double squaredDistanceTo(Candidate candidate, int[] pos) {
        double d0 = pos[0] - candidate.x();
        double d1 = pos[1] - candidate.y();
        double d2 = pos[2] - candidate.z();
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    /** The reference's wrapDegrees, verbatim. */
    public static int wrapDegrees(int degrees) {
        degrees %= 360;
        if (degrees >= 160) {
            degrees -= 360;
        }
        if (degrees < 0) {
            degrees += 360;
        }
        return degrees;
    }

    private static int[] parsePos(Map<String, String> args, int[] defaultValue) {
        return new int[] {
                parseInt(args, "x", defaultValue[0]),
                parseInt(args, "y", defaultValue[1]),
                parseInt(args, "z", defaultValue[2])
        };
    }

    private static boolean hasPosArg(Map<String, String> args) {
        for (String s : POS_ARGS) {
            if (args.containsKey(s)) {
                return true;
            }
        }
        return false;
    }

    private static int parseInt(Map<String, String> args, String key, int defaultValue) {
        return args.containsKey(key) ? parseLoose(args.get(key), defaultValue) : defaultValue;
    }

    /** MathHelper.parseInt: the value or the default on any parse failure. */
    private static int parseLoose(String s, int defaultValue) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String getArg(Map<String, String> args, String key) {
        return args.get(key);
    }

    /** The reference's parseScoreboardScores: every score_ key, default value 1. */
    public static Map<String, Integer> parseScoreboardScores(Map<String, String> args) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String s : args.keySet()) {
            if (s.startsWith("score_") && s.length() > "score_".length()) {
                map.put(s.substring("score_".length()), parseLoose(args.get(s), 1));
            }
        }
        return map;
    }

    /** The reference's matchesMultiple: whether the selector can answer more than one. */
    public static boolean matchesMultiple(String s) {
        Matcher matcher = TARGET_SELECTOR_PATTERN.matcher(s);
        if (!matcher.matches()) {
            return false;
        }
        Map<String, String> map = parseArgs(matcher.group(2));
        String sx = matcher.group(1);
        int i = !"a".equals(sx) && !"e".equals(sx) ? 1 : 0;
        return parseInt(map, "c", i) != 1;
    }

    /** The reference's isValid. */
    public static boolean isValid(String s) {
        return TARGET_SELECTOR_PATTERN.matcher(s).matches();
    }

    /** The reference's parseArgs: the positional short form then the key=value list. */
    static Map<String, String> parseArgs(String s) {
        Map<String, String> map = new HashMap<>();
        if (s == null) {
            return map;
        }

        int i = 0;
        int j = -1;
        Matcher matcher = VALUE_PATTERN.matcher(s);
        while (matcher.find()) {
            String sx = null;
            switch (i++) {
                case 0 -> sx = "x";
                case 1 -> sx = "y";
                case 2 -> sx = "z";
                case 3 -> sx = "r";
                default -> {
                }
            }
            if (sx != null && matcher.group(1).length() > 0) {
                map.put(sx, matcher.group(1));
            }
            j = matcher.end();
        }

        if (j < s.length()) {
            Matcher matcher1 = ARG_PATTERN.matcher(j == -1 ? s : s.substring(j));
            while (matcher1.find()) {
                map.put(matcher1.group(1), matcher1.group(2));
            }
        }

        return map;
    }

    /** The reference's SourceView over the engine's CommandSender — the command layer's adapter. */
    public static SourceView sourceViewOf(
            net.zaminmc.torch.server.chat.CommandSender sender) {
        // The command-source surface (CommandSender's own defaults carry the
        // player/console shapes; /execute's proxied source overrides all
        // three — the parsed coords, the entity's dimension, the entity).
        double[] pos = sender.sourcePos();
        int dimension = sender.sourceDimension();
        Candidate self = sender.asSourceEntity();
        return new SourceView() {
            @Override public int[] blockPos() {
                return new int[] {
                        (int) Math.floor(pos[0]),
                        (int) Math.floor(pos[1]),
                        (int) Math.floor(pos[2]) };
            }
            @Override public int dimension() { return dimension; }
            @Override public Candidate asEntity() { return self; }
            @Override public boolean canUseSelectors() { return sender.opLevel() >= 1; }
            @Override public void sendInvalidType(String typeName) {
                sender.sendMessage("Unknown entity type: " + typeName);
            }
        };
    }

    private static EngineScoreboard board() {
        java.util.function.Supplier<EngineScoreboard> supplier = boardSupplier;
        return supplier == null ? null : supplier.get();
    }
}
