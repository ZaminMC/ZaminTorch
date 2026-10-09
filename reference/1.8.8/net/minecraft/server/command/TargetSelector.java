package net.minecraft.server.command;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;

public class TargetSelector {
    private static final Pattern TARGET_SELECTOR_PATTERN = Pattern.compile("^@([pare])(?:\\[([\\w=,!-]*)\\])?$");
    private static final Pattern VALUE_PATTERN = Pattern.compile("\\G([-!]?[\\w-]*)(?:$|,)");
    private static final Pattern ARG_PATTERN = Pattern.compile("\\G(\\w+)=([-!]?[\\w-]*)(?:$|,)");
    private static final Set<String> POS_ARGS = Sets.newHashSet("x", "y", "z", "dx", "dy", "dz", "rm", "r");

    public static ServerPlayerEntity selectFirstPlayer(CommandSource source, String s) {
        return selectFirst(source, s, ServerPlayerEntity.class);
    }

    public static <T extends Entity> T selectFirst(CommandSource source, String s, Class<? extends T> type) {
        List<T> list = select(source, s, type);
        return list.size() == 1 ? list.get(0) : null;
    }

    public static Text getSelectionAsText(CommandSource source, String s) {
        List<Entity> list = select(source, s, Entity.class);
        if (list.isEmpty()) {
            return null;
        }

        List<Text> list1 = Lists.newArrayList();

        for (Entity entity : list) {
            list1.add(entity.getDisplayName());
        }

        return AbstractCommand.listText(list1);
    }

    public static <T extends Entity> List<T> select(CommandSource source, String s, Class<? extends T> type) {
        Matcher matcher = TARGET_SELECTOR_PATTERN.matcher(s);
        if (matcher.matches() && source.canUseCommand(1, "@")) {
            Map<String, String> map = parseArgs(matcher.group(2));
            if (!hasTypeArg(source, map)) {
                return Collections.emptyList();
            }

            String sx = matcher.group(1);
            BlockPos blockpos = parsePos(map, source.getCommandSourceBlockPos());
            List<World> list = getWorlds(source, map);
            List<T> list1 = Lists.newArrayList();

            for (World world : list) {
                if (world != null) {
                    List<Predicate<Entity>> list2 = Lists.newArrayList();
                    list2.addAll(getTypePredicates(map, sx));
                    list2.addAll(getXpPredicates(map));
                    list2.addAll(getGameModePredicates(map));
                    list2.addAll(getTeamPredicates(map));
                    list2.addAll(getScoreboardPredicates(map));
                    list2.addAll(getNamePredicates(map));
                    list2.addAll(getDistancePredicates(map, blockpos));
                    list2.addAll(getRotationPredicates(map));
                    list1.addAll(select(map, type, list2, sx, world, blockpos));
                }
            }

            return filterSelection(list1, map, source, type, sx, blockpos);
        } else {
            return Collections.emptyList();
        }
    }

    private static List<World> getWorlds(CommandSource source, Map<String, String> args) {
        List<World> list = Lists.newArrayList();
        if (hasPosArg(args)) {
            list.add(source.getCommandSourceWorld());
        } else {
            Collections.addAll(list, MinecraftServer.getInstance().worlds);
        }

        return list;
    }

    private static <T extends Entity> boolean hasTypeArg(CommandSource source, Map<String, String> args) {
        String s = getArg(args, "type");
        s = s != null && s.startsWith("!") ? s.substring(1) : s;
        if (s != null && !Entities.isKey(s)) {
            TranslatableText translatabletext = new TranslatableText("commands.generic.entity.invalidType", s);
            translatabletext.getStyle().setColor(Formatting.RED);
            source.sendMessage(translatabletext);
            return false;
        } else {
            return true;
        }
    }

    private static List<Predicate<Entity>> getTypePredicates(Map<String, String> args, String s) {
        List<Predicate<Entity>> list = Lists.newArrayList();
        String sx = getArg(args, "type");
        final boolean flag = sx != null && sx.startsWith("!");
        if (flag) {
            sx = sx.substring(1);
        }

        final String s1 = sx;
        boolean flag1 = !s.equals("e");
        boolean flag2 = s.equals("r") && sx != null;
        if ((sx == null || !s.equals("e")) && !flag2) {
            if (flag1) {
                list.add(new Predicate<Entity>() {
                    public boolean apply(Entity entity) {
                        return entity instanceof PlayerEntity;
                    }
                });
            }
        } else {
            list.add(new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    return Entities.is(entity, s1) != flag;
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getXpPredicates(Map<String, String> args) {
        List<Predicate<Entity>> list = Lists.newArrayList();
        final int i = parseInt(args, "lm", -1);
        final int j = parseInt(args, "l", -1);
        if (i > -1 || j > -1) {
            list.add(new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    if (!(entity instanceof ServerPlayerEntity)) {
                        return false;
                    }

                    ServerPlayerEntity serverplayerentity = (ServerPlayerEntity)entity;
                    return (i <= -1 || serverplayerentity.xpLevel >= i) && (j <= -1 || serverplayerentity.xpLevel <= j);
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getGameModePredicates(Map<String, String> args) {
        List<Predicate<Entity>> list = Lists.newArrayList();
        final int i = parseInt(args, "m", WorldSettings.GameMode.NOT_SET.getId());
        if (i != WorldSettings.GameMode.NOT_SET.getId()) {
            list.add(new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    if (!(entity instanceof ServerPlayerEntity)) {
                        return false;
                    }

                    ServerPlayerEntity serverplayerentity = (ServerPlayerEntity)entity;
                    return serverplayerentity.interactionManager.getGameMode().getId() == i;
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getTeamPredicates(Map<String, String> args) {
        List<Predicate<Entity>> list = Lists.newArrayList();
        String s = getArg(args, "team");
        final boolean flag = s != null && s.startsWith("!");
        if (flag) {
            s = s.substring(1);
        }

        final String s1 = s;
        if (s != null) {
            list.add(new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    if (!(entity instanceof LivingEntity)) {
                        return false;
                    }

                    LivingEntity livingentity = (LivingEntity)entity;
                    AbstractTeam abstractteam = livingentity.getScoreboardTeam();
                    String s2 = abstractteam == null ? "" : abstractteam.getName();
                    return s2.equals(s1) != flag;
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getScoreboardPredicates(Map<String, String> args) {
        List<Predicate<Entity>> list = Lists.newArrayList();
        final Map<String, Integer> map = parseScoreboardScores(args);
        if (map != null && map.size() > 0) {
            list.add(new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    Scoreboard scoreboard = MinecraftServer.getInstance().getWorld(0).getScoreboard();

                    for (Entry<String, Integer> entry : map.entrySet()) {
                        String s = entry.getKey();
                        boolean flag = false;
                        if (s.endsWith("_min") && s.length() > 4) {
                            flag = true;
                            s = s.substring(0, s.length() - 4);
                        }

                        ScoreboardObjective scoreboardobjective = scoreboard.getObjective(s);
                        if (scoreboardobjective == null) {
                            return false;
                        }

                        String s1 = entity instanceof ServerPlayerEntity ? entity.getName() : entity.getUuid().toString();
                        if (!scoreboard.hasScore(s1, scoreboardobjective)) {
                            return false;
                        }

                        ScoreboardScore scoreboardscore = scoreboard.getScore(s1, scoreboardobjective);
                        int i = scoreboardscore.get();
                        if (i < entry.getValue() && flag) {
                            return false;
                        }

                        if (i > entry.getValue() && !flag) {
                            return false;
                        }
                    }

                    return true;
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getNamePredicates(Map<String, String> args) {
        List<Predicate<Entity>> list = Lists.newArrayList();
        String s = getArg(args, "name");
        final boolean flag = s != null && s.startsWith("!");
        if (flag) {
            s = s.substring(1);
        }

        final String s1 = s;
        if (s != null) {
            list.add(new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    return entity.getName().equals(s1) != flag;
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getDistancePredicates(Map<String, String> args, BlockPos pos) {
        List<Predicate<Entity>> list = Lists.newArrayList();
        final int i = parseInt(args, "rm", -1);
        final int j = parseInt(args, "r", -1);
        if (pos != null && (i >= 0 || j >= 0)) {
            final int k = i * i;
            final int l = j * j;
            list.add(new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    int i1 = (int)entity.getSquaredDistanceToCenter(pos);
                    return (i < 0 || i1 >= k) && (j < 0 || i1 <= l);
                }
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getRotationPredicates(Map<String, String> args) {
        List<Predicate<Entity>> list = Lists.newArrayList();
        if (args.containsKey("rym") || args.containsKey("ry")) {
            final int i = wrapDegrees(parseInt(args, "rym", 0));
            final int j = wrapDegrees(parseInt(args, "ry", 359));
            list.add(new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    int i1 = TargetSelector.wrapDegrees((int)Math.floor(entity.yaw));
                    return i > j ? i1 >= i || i1 <= j : i1 >= i && i1 <= j;
                }
            });
        }

        if (args.containsKey("rxm") || args.containsKey("rx")) {
            final int k = wrapDegrees(parseInt(args, "rxm", 0));
            final int l = wrapDegrees(parseInt(args, "rx", 359));
            list.add(new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    int i1 = TargetSelector.wrapDegrees((int)Math.floor(entity.pitch));
                    return k > l ? i1 >= k || i1 <= l : i1 >= k && i1 <= l;
                }
            });
        }

        return list;
    }

    private static <T extends Entity> List<T> select(
        Map<String, String> args, Class<? extends T> type, List<Predicate<Entity>> predicates, String rawTarget, World world, BlockPos pos
    ) {
        List<T> list = Lists.newArrayList();
        String s = getArg(args, "type");
        s = s != null && s.startsWith("!") ? s.substring(1) : s;
        boolean flag = !rawTarget.equals("e");
        boolean flag1 = rawTarget.equals("r") && s != null;
        int i = parseInt(args, "dx", 0);
        int j = parseInt(args, "dy", 0);
        int k = parseInt(args, "dz", 0);
        int l = parseInt(args, "r", -1);
        Predicate<Entity> predicate = Predicates.and(predicates);
        Predicate<Entity> predicate1 = Predicates.and(EntityFilter.ALIVE, predicate);
        if (pos != null) {
            int i1 = world.players.size();
            int j1 = world.entities.size();
            boolean flag2 = i1 < j1 / 16;
            if (args.containsKey("dx") || args.containsKey("dy") || args.containsKey("dz")) {
                final Box box1 = getBox(pos, i, j, k);
                if (flag && flag2 && !flag1) {
                    Predicate<Entity> predicate2 = new Predicate<Entity>() {
                        public boolean apply(Entity entity) {
                            return !(entity.x < box1.minX)
                                && !(entity.y < box1.minY)
                                && !(entity.z < box1.minZ)
                                && !(entity.x >= box1.maxX)
                                && !(entity.y >= box1.maxY)
                                && !(entity.z >= box1.maxZ);
                        }
                    };
                    list.addAll(world.getPlayers(type, Predicates.and(predicate1, predicate2)));
                } else {
                    list.addAll(world.getEntitiesOfType(type, box1, predicate1));
                }
            } else if (l >= 0) {
                Box box = new Box(pos.getX() - l, pos.getY() - l, pos.getZ() - l, pos.getX() + l + 1, pos.getY() + l + 1, pos.getZ() + l + 1);
                if (flag && flag2 && !flag1) {
                    list.addAll(world.getPlayers(type, predicate1));
                } else {
                    list.addAll(world.getEntitiesOfType(type, box, predicate1));
                }
            } else if (rawTarget.equals("a")) {
                list.addAll(world.getPlayers(type, predicate));
            } else if (!rawTarget.equals("p") && (!rawTarget.equals("r") || flag1)) {
                list.addAll(world.getEntities(type, predicate1));
            } else {
                list.addAll(world.getPlayers(type, predicate1));
            }
        } else if (rawTarget.equals("a")) {
            list.addAll(world.getPlayers(type, predicate));
        } else if (!rawTarget.equals("p") && (!rawTarget.equals("r") || flag1)) {
            list.addAll(world.getEntities(type, predicate1));
        } else {
            list.addAll(world.getPlayers(type, predicate1));
        }

        return list;
    }

    private static <T extends Entity> List<T> filterSelection(
        List<T> entities, Map<String, String> args, CommandSource source, Class<? extends T> type, String rawTarget, BlockPos pos
    ) {
        int i = parseInt(args, "c", !rawTarget.equals("a") && !rawTarget.equals("e") ? 1 : 0);
        if (!rawTarget.equals("p") && !rawTarget.equals("a") && !rawTarget.equals("e")) {
            if (rawTarget.equals("r")) {
                Collections.shuffle(entities);
            }
        } else if (pos != null) {
            Collections.sort(entities, new Comparator<Entity>() {
                public int compare(Entity entity, Entity entity2) {
                    return ComparisonChain.start().compare(entity.getSquaredDistanceTo(pos), entity2.getSquaredDistanceTo(pos)).result();
                }
            });
        }

        Entity entity = source.asEntity();
        if (entity != null && type.isAssignableFrom(entity.getClass()) && i == 1 && entities.contains(entity) && !"r".equals(rawTarget)) {
            entities = Lists.newArrayList((T[])(new Entity[]{entity}));
        }

        if (i != 0) {
            if (i < 0) {
                Collections.reverse(entities);
            }

            entities = entities.subList(0, Math.min(Math.abs(i), entities.size()));
        }

        return entities;
    }

    private static Box getBox(BlockPos pos, int dx, int dy, int dz) {
        boolean flag = dx < 0;
        boolean flag1 = dy < 0;
        boolean flag2 = dz < 0;
        int i = pos.getX() + (flag ? dx : 0);
        int j = pos.getY() + (flag1 ? dy : 0);
        int k = pos.getZ() + (flag2 ? dz : 0);
        int l = pos.getX() + (flag ? 0 : dx) + 1;
        int i1 = pos.getY() + (flag1 ? 0 : dy) + 1;
        int j1 = pos.getZ() + (flag2 ? 0 : dz) + 1;
        return new Box(i, j, k, l, i1, j1);
    }

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

    private static BlockPos parsePos(Map<String, String> args, BlockPos defaultValue) {
        return new BlockPos(parseInt(args, "x", defaultValue.getX()), parseInt(args, "y", defaultValue.getY()), parseInt(args, "z", defaultValue.getZ()));
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
        return args.containsKey(key) ? MathHelper.parseInt(args.get(key), defaultValue) : defaultValue;
    }

    private static String getArg(Map<String, String> args, String key) {
        return args.get(key);
    }

    public static Map<String, Integer> parseScoreboardScores(Map<String, String> args) {
        Map<String, Integer> map = Maps.newHashMap();

        for (String s : args.keySet()) {
            if (s.startsWith("score_") && s.length() > "score_".length()) {
                map.put(s.substring("score_".length()), MathHelper.parseInt(args.get(s), 1));
            }
        }

        return map;
    }

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

    public static boolean isValid(String s) {
        return TARGET_SELECTOR_PATTERN.matcher(s).matches();
    }

    private static Map<String, String> parseArgs(String s) {
        Map<String, String> map = Maps.newHashMap();
        if (s == null) {
            return map;
        }

        int i = 0;
        int j = -1;

        for (Matcher matcher = VALUE_PATTERN.matcher(s); matcher.find(); j = matcher.end()) {
            String sx = null;
            switch (i++) {
                case 0:
                    sx = "x";
                    break;
                case 1:
                    sx = "y";
                    break;
                case 2:
                    sx = "z";
                    break;
                case 3:
                    sx = "r";
            }

            if (sx != null && matcher.group(1).length() > 0) {
                map.put(sx, matcher.group(1));
            }
        }

        if (j < s.length()) {
            Matcher matcher1 = ARG_PATTERN.matcher(j == -1 ? s : s.substring(j));

            while (matcher1.find()) {
                map.put(matcher1.group(1), matcher1.group(2));
            }
        }

        return map;
    }
}
