package net.minecraft.server.command;

import java.util.List;
import java.util.Random;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldData;

public class WeatherCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.weather.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length >= 1 && args.length <= 2) {
            int i = (300 + new Random().nextInt(600)) * 20;
            if (args.length >= 2) {
                i = parseInt(args[1], 1, 1000000) * 20;
            }

            World world = MinecraftServer.getInstance().worlds[0];
            WorldData worlddata = world.getData();
            if ("clear".equalsIgnoreCase(args[0])) {
                worlddata.setClearWeatherTime(i);
                worlddata.setRainTime(0);
                worlddata.setThunderTime(0);
                worlddata.setRaining(false);
                worlddata.setThundering(false);
                sendSuccess(source, this, "commands.weather.clear");
            } else if ("rain".equalsIgnoreCase(args[0])) {
                worlddata.setClearWeatherTime(0);
                worlddata.setRainTime(i);
                worlddata.setThunderTime(i);
                worlddata.setRaining(true);
                worlddata.setThundering(false);
                sendSuccess(source, this, "commands.weather.rain");
            } else {
                if (!"thunder".equalsIgnoreCase(args[0])) {
                    throw new IncorrectUsageException("commands.weather.usage");
                }

                worlddata.setClearWeatherTime(0);
                worlddata.setRainTime(i);
                worlddata.setThunderTime(i);
                worlddata.setRaining(true);
                worlddata.setThundering(true);
                sendSuccess(source, this, "commands.weather.thunder");
            }
        } else {
            throw new IncorrectUsageException("commands.weather.usage");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 1 ? suggestMatching(args, "clear", "rain", "thunder") : null;
    }
}
