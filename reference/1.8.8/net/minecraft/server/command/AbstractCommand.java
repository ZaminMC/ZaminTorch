package net.minecraft.server.command;

import com.google.common.base.Functions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.resource.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.EntityNotFoundException;
import net.minecraft.server.command.exception.InvalidNumberException;
import net.minecraft.server.command.exception.PlayerNotFoundException;
import net.minecraft.server.command.handler.CommandListener;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public abstract class AbstractCommand implements Command {
    private static CommandListener listener;

    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public boolean canUse(CommandSource source) {
        return source.canUseCommand(this.getRequiredPermissionLevel(), this.getName());
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return null;
    }

    public static int parseInt(String s) throws InvalidNumberException {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException numberformatexception) {
            throw new InvalidNumberException("commands.generic.num.invalid", s);
        }
    }

    public static int parseInt(String s, int min) throws InvalidNumberException {
        return parseInt(s, min, Integer.MAX_VALUE);
    }

    public static int parseInt(String s, int min, int max) throws InvalidNumberException {
        int i = parseInt(s);
        if (i < min) {
            throw new InvalidNumberException("commands.generic.num.tooSmall", i, min);
        } else if (i > max) {
            throw new InvalidNumberException("commands.generic.num.tooBig", i, max);
        } else {
            return i;
        }
    }

    public static long parseLong(String s) throws InvalidNumberException {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException numberformatexception) {
            throw new InvalidNumberException("commands.generic.num.invalid", s);
        }
    }

    public static long parseLong(String s, long min, long max) throws InvalidNumberException {
        long i = parseLong(s);
        if (i < min) {
            throw new InvalidNumberException("commands.generic.num.tooSmall", i, min);
        } else if (i > max) {
            throw new InvalidNumberException("commands.generic.num.tooBig", i, max);
        } else {
            return i;
        }
    }

    public static BlockPos parseBlockPos(CommandSource source, String[] args, int startIndex, boolean center) throws InvalidNumberException {
        BlockPos blockpos = source.getCommandSourceBlockPos();
        return new BlockPos(
            parseCoordinate(blockpos.getX(), args[startIndex], -30000000, 30000000, center),
            parseCoordinate(blockpos.getY(), args[startIndex + 1], 0, 256, false),
            parseCoordinate(blockpos.getZ(), args[startIndex + 2], -30000000, 30000000, center)
        );
    }

    public static double parseDouble(String s) throws InvalidNumberException {
        try {
            double d0 = Double.parseDouble(s);
            if (!Doubles.isFinite(d0)) {
                throw new InvalidNumberException("commands.generic.num.invalid", s);
            } else {
                return d0;
            }
        } catch (NumberFormatException numberformatexception) {
            throw new InvalidNumberException("commands.generic.num.invalid", s);
        }
    }

    public static double parseDouble(String s, double min) throws InvalidNumberException {
        return parseDouble(s, min, Double.MAX_VALUE);
    }

    public static double parseDouble(String s, double min, double max) throws InvalidNumberException {
        double d0 = parseDouble(s);
        if (d0 < min) {
            throw new InvalidNumberException("commands.generic.double.tooSmall", d0, min);
        } else if (d0 > max) {
            throw new InvalidNumberException("commands.generic.double.tooBig", d0, max);
        } else {
            return d0;
        }
    }

    public static boolean parseBoolean(String s) throws CommandException {
        if (s.equals("true") || s.equals("1")) {
            return true;
        } else if (!s.equals("false") && !s.equals("0")) {
            throw new CommandException("commands.generic.boolean.invalid", s);
        } else {
            return false;
        }
    }

    public static ServerPlayerEntity asPlayer(CommandSource source) throws PlayerNotFoundException {
        if (source instanceof ServerPlayerEntity) {
            return (ServerPlayerEntity)source;
        } else {
            throw new PlayerNotFoundException("You must specify which player you wish to perform this action on.");
        }
    }

    public static ServerPlayerEntity parsePlayer(CommandSource source, String s) throws PlayerNotFoundException {
        ServerPlayerEntity serverplayerentity = TargetSelector.selectFirstPlayer(source, s);
        if (serverplayerentity == null) {
            try {
                serverplayerentity = MinecraftServer.getInstance().getPlayerManager().get(UUID.fromString(s));
            } catch (IllegalArgumentException illegalargumentexception) {
            }
        }

        if (serverplayerentity == null) {
            serverplayerentity = MinecraftServer.getInstance().getPlayerManager().get(s);
        }

        if (serverplayerentity == null) {
            throw new PlayerNotFoundException();
        } else {
            return serverplayerentity;
        }
    }

    public static Entity parseEntity(CommandSource source, String s) throws EntityNotFoundException {
        return parseEntity(source, s, Entity.class);
    }

    public static <T extends Entity> T parseEntity(CommandSource source, String s, Class<? extends T> type) throws EntityNotFoundException {
        Entity entity = TargetSelector.selectFirst(source, s, type);
        MinecraftServer minecraftserver = MinecraftServer.getInstance();
        if (entity == null) {
            entity = minecraftserver.getPlayerManager().get(s);
        }

        if (entity == null) {
            try {
                UUID uuid = UUID.fromString(s);
                entity = minecraftserver.getEntity(uuid);
                if (entity == null) {
                    entity = minecraftserver.getPlayerManager().get(uuid);
                }
            } catch (IllegalArgumentException illegalargumentexception) {
                throw new EntityNotFoundException("commands.generic.entity.invalidUuid");
            }
        }

        if (entity != null && type.isAssignableFrom(entity.getClass())) {
            return (T)entity;
        } else {
            throw new EntityNotFoundException();
        }
    }

    public static List<Entity> parseEntities(CommandSource source, String s) throws EntityNotFoundException {
        return TargetSelector.isValid(s) ? TargetSelector.select(source, s, Entity.class) : Lists.newArrayList(parseEntity(source, s));
    }

    public static String parsePlayerName(CommandSource source, String s) throws PlayerNotFoundException {
        try {
            return parsePlayer(source, s).getName();
        } catch (PlayerNotFoundException playernotfoundexception) {
            if (TargetSelector.isValid(s)) {
                throw playernotfoundexception;
            } else {
                return s;
            }
        }
    }

    public static String parseEntityName(CommandSource source, String s) throws EntityNotFoundException {
        try {
            return parsePlayer(source, s).getName();
        } catch (PlayerNotFoundException playernotfoundexception) {
            try {
                return parseEntity(source, s).getUuid().toString();
            } catch (EntityNotFoundException entitynotfoundexception) {
                if (TargetSelector.isValid(s)) {
                    throw entitynotfoundexception;
                } else {
                    return s;
                }
            }
        }
    }

    public static Text parseText(CommandSource source, String[] args, int startIndex) throws PlayerNotFoundException {
        return parseText(source, args, startIndex, false);
    }

    public static Text parseText(CommandSource source, String[] args, int startIndex, boolean parseEntityNames) throws PlayerNotFoundException {
        Text text = new LiteralText("");

        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                text.append(" ");
            }

            Text text1 = new LiteralText(args[i]);
            if (parseEntityNames) {
                Text text2 = TargetSelector.getSelectionAsText(source, args[i]);
                if (text2 == null) {
                    if (TargetSelector.isValid(args[i])) {
                        throw new PlayerNotFoundException();
                    }
                } else {
                    text1 = text2;
                }
            }

            text.append(text1);
        }

        return text;
    }

    public static String parseString(String[] args, int startIndex) {
        StringBuilder stringbuilder = new StringBuilder();

        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                stringbuilder.append(" ");
            }

            String s = args[i];
            stringbuilder.append(s);
        }

        return stringbuilder.toString();
    }

    public static AbstractCommand.Coordinate parseTeleportCoordinate(double c, String s, boolean center) throws InvalidNumberException {
        return parseTeleportCoordinate(c, s, -30000000, 30000000, center);
    }

    public static AbstractCommand.Coordinate parseTeleportCoordinate(double c, String s, int min, int max, boolean center) throws InvalidNumberException {
        boolean flag = s.startsWith("~");
        if (flag && Double.isNaN(c)) {
            throw new InvalidNumberException("commands.generic.num.invalid", c);
        }

        double d0 = 0.0;
        if (!flag || s.length() > 1) {
            boolean flag1 = s.contains(".");
            if (flag) {
                s = s.substring(1);
            }

            d0 += parseDouble(s);
            if (!flag1 && !flag && center) {
                d0 += 0.5;
            }
        }

        if (min != 0 || max != 0) {
            if (d0 < min) {
                throw new InvalidNumberException("commands.generic.double.tooSmall", d0, min);
            }

            if (d0 > max) {
                throw new InvalidNumberException("commands.generic.double.tooBig", d0, max);
            }
        }

        return new AbstractCommand.Coordinate(d0 + (flag ? c : 0.0), d0, flag);
    }

    public static double parseCoordinate(double c, String s, boolean center) throws InvalidNumberException {
        return parseCoordinate(c, s, -30000000, 30000000, center);
    }

    public static double parseCoordinate(double c, String s, int min, int max, boolean center) throws InvalidNumberException {
        boolean flag = s.startsWith("~");
        if (flag && Double.isNaN(c)) {
            throw new InvalidNumberException("commands.generic.num.invalid", c);
        }

        double d0 = flag ? c : 0.0;
        if (!flag || s.length() > 1) {
            boolean flag1 = s.contains(".");
            if (flag) {
                s = s.substring(1);
            }

            d0 += parseDouble(s);
            if (!flag1 && !flag && center) {
                d0 += 0.5;
            }
        }

        if (min != 0 || max != 0) {
            if (d0 < min) {
                throw new InvalidNumberException("commands.generic.double.tooSmall", d0, min);
            }

            if (d0 > max) {
                throw new InvalidNumberException("commands.generic.double.tooBig", d0, max);
            }
        }

        return d0;
    }

    public static Item parseItem(CommandSource source, String s) throws InvalidNumberException {
        Identifier identifier = new Identifier(s);
        Item item = Item.REGISTRY.get(identifier);
        if (item == null) {
            throw new InvalidNumberException("commands.give.item.notFound", identifier);
        } else {
            return item;
        }
    }

    public static Block parseBlock(CommandSource source, String s) throws InvalidNumberException {
        Identifier identifier = new Identifier(s);
        if (!Block.REGISTRY.containsKey(identifier)) {
            throw new InvalidNumberException("commands.give.block.notFound", identifier);
        } else {
            Block block = Block.REGISTRY.get(identifier);
            if (block == null) {
                throw new InvalidNumberException("commands.give.block.notFound", identifier);
            } else {
                return block;
            }
        }
    }

    public static String listArgs(Object[] args) {
        StringBuilder stringbuilder = new StringBuilder();

        for (int i = 0; i < args.length; i++) {
            String s = args[i].toString();
            if (i > 0) {
                if (i == args.length - 1) {
                    stringbuilder.append(" and ");
                } else {
                    stringbuilder.append(", ");
                }
            }

            stringbuilder.append(s);
        }

        return stringbuilder.toString();
    }

    public static Text listText(List<Text> text) {
        Text textx = new LiteralText("");

        for (int i = 0; i < text.size(); i++) {
            if (i > 0) {
                if (i == text.size() - 1) {
                    textx.append(" and ");
                } else if (i > 0) {
                    textx.append(", ");
                }
            }

            textx.append(text.get(i));
        }

        return textx;
    }

    public static String listArgs(Collection<String> args) {
        return listArgs(args.toArray(new String[args.size()]));
    }

    public static List<String> suggestCoordinate(String[] args, int index, BlockPos pos) {
        if (pos == null) {
            return null;
        }

        int i = args.length - 1;
        String s;
        if (i == index) {
            s = Integer.toString(pos.getX());
        } else if (i == index + 1) {
            s = Integer.toString(pos.getY());
        } else {
            if (i != index + 2) {
                return null;
            }

            s = Integer.toString(pos.getZ());
        }

        return Lists.newArrayList(s);
    }

    public static List<String> suggestHorizontalCoordinate(String[] args, int index, BlockPos pos) {
        if (pos == null) {
            return null;
        }

        int i = args.length - 1;
        String s;
        if (i == index) {
            s = Integer.toString(pos.getX());
        } else {
            if (i != index + 1) {
                return null;
            }

            s = Integer.toString(pos.getZ());
        }

        return Lists.newArrayList(s);
    }

    public static boolean doesStringStartWith(String region, String s) {
        return s.regionMatches(true, 0, region, 0, region.length());
    }

    public static List<String> suggestMatching(String[] args, String... suggestions) {
        return suggestMatching(args, Arrays.asList(suggestions));
    }

    public static List<String> suggestMatching(String[] args, Collection<?> suggestions) {
        String s = args[args.length - 1];
        List<String> list = Lists.newArrayList();
        if (!suggestions.isEmpty()) {
            for (String s1 : Iterables.transform((Iterable<Object>)suggestions, Functions.toStringFunction())) {
                if (doesStringStartWith(s, s1)) {
                    list.add(s1);
                }
            }

            if (list.isEmpty()) {
                for (Object object : suggestions) {
                    if (object instanceof Identifier && doesStringStartWith(s, ((Identifier)object).getPath())) {
                        list.add(String.valueOf(object));
                    }
                }
            }
        }

        return list;
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return false;
    }

    public static void sendSuccess(CommandSource source, Command command, String message, Object... args) {
        sendSuccess(source, command, 0, message, args);
    }

    public static void sendSuccess(CommandSource source, Command command, int flags, String message, Object... args) {
        if (listener != null) {
            listener.sendSuccess(source, command, flags, message, args);
        }
    }

    public static void setListener(CommandListener listener) {
        AbstractCommand.listener = listener;
    }

    public int compareTo(Command command) {
        return this.getName().compareTo(command.getName());
    }

    public static class Coordinate {
        private final double coordinate;
        private final double input;
        private final boolean relative;

        protected Coordinate(double coordinate, double input, boolean relative) {
            this.coordinate = coordinate;
            this.input = input;
            this.relative = relative;
        }

        public double getCoordinate() {
            return this.coordinate;
        }

        public double getInput() {
            return this.input;
        }

        public boolean isRelative() {
            return this.relative;
        }
    }
}
