package net.minecraft.server.command;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.Profiler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DebugCommand extends AbstractCommand {
    private static final Logger LOGGER = LogManager.getLogger();
    private long startMillis;
    private int startTicks;

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.debug.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new IncorrectUsageException("commands.debug.usage");
        }

        if (args[0].equals("start")) {
            if (args.length != 1) {
                throw new IncorrectUsageException("commands.debug.usage");
            }

            sendSuccess(source, this, "commands.debug.start");
            MinecraftServer.getInstance().enableProfiling();
            this.startMillis = MinecraftServer.getTimeMillis();
            this.startTicks = MinecraftServer.getInstance().getTicks();
        } else {
            if (!args[0].equals("stop")) {
                throw new IncorrectUsageException("commands.debug.usage");
            }

            if (args.length != 1) {
                throw new IncorrectUsageException("commands.debug.usage");
            }

            if (!MinecraftServer.getInstance().profiler.profiling) {
                throw new CommandException("commands.debug.notStarted");
            }

            long i = MinecraftServer.getTimeMillis();
            int j = MinecraftServer.getInstance().getTicks();
            long k = i - this.startMillis;
            int l = j - this.startTicks;
            this.stop(k, l);
            MinecraftServer.getInstance().profiler.profiling = false;
            sendSuccess(source, this, "commands.debug.stop", (float)k / 1000.0F, l);
        }
    }

    private void stop(long durationMillis, int durationTicks) {
        File file1 = new File(
            MinecraftServer.getInstance().getFile("debug"), "profile-results-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".txt"
        );
        file1.getParentFile().mkdirs();

        try {
            FileWriter filewriter = new FileWriter(file1);
            filewriter.write(this.buildReport(durationMillis, durationTicks));
            filewriter.close();
        } catch (Throwable throwable) {
            LOGGER.error("Could not save profiler results to " + file1, throwable);
        }
    }

    private String buildReport(long durationMillis, int durationTicks) {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append("---- Minecraft Profiler Results ----\n");
        stringbuilder.append("// ");
        stringbuilder.append(getWittyComment());
        stringbuilder.append("\n\n");
        stringbuilder.append("Time span: ").append(durationMillis).append(" ms\n");
        stringbuilder.append("Tick span: ").append(durationTicks).append(" ticks\n");
        stringbuilder.append("// This is approximately ")
            .append(String.format("%.2f", durationTicks / ((float)durationMillis / 1000.0F)))
            .append(" ticks per second. It should be ")
            .append(20)
            .append(" ticks per second\n\n");
        stringbuilder.append("--- BEGIN PROFILE DUMP ---\n\n");
        this.addResults(0, "root", stringbuilder);
        stringbuilder.append("--- END PROFILE DUMP ---\n\n");
        return stringbuilder.toString();
    }

    private void addResults(int spacing, String location, StringBuilder report) {
        List<Profiler.Result> list = MinecraftServer.getInstance().profiler.getResults(location);
        if (list != null && list.size() >= 3) {
            for (int i = 1; i < list.size(); i++) {
                Profiler.Result profiler$result = list.get(i);
                report.append(String.format("[%02d] ", spacing));

                for (int j = 0; j < spacing; j++) {
                    report.append(" ");
                }

                report.append(profiler$result.location)
                    .append(" - ")
                    .append(String.format("%.2f", profiler$result.percentageOfParent))
                    .append("%/")
                    .append(String.format("%.2f", profiler$result.percentageOfTotal))
                    .append("%\n");
                if (!profiler$result.location.equals("unspecified")) {
                    try {
                        this.addResults(spacing + 1, location + "." + profiler$result.location, report);
                    } catch (Exception exception) {
                        report.append("[[ EXCEPTION ").append(exception).append(" ]]");
                    }
                }
            }
        }
    }

    private static String getWittyComment() {
        String[] astring = new String[]{
            "Shiny numbers!",
            "Am I not running fast enough? :(",
            "I'm working as hard as I can!",
            "Will I ever be good enough for you? :(",
            "Speedy. Zoooooom!",
            "Hello world",
            "40% better than a crash report.",
            "Now with extra numbers",
            "Now with less numbers",
            "Now with the same numbers",
            "You should add flames to things, it makes them go faster!",
            "Do you feel the need for... optimization?",
            "*cracks redstone whip*",
            "Maybe if you treated it better then it'll have more motivation to work faster! Poor server."
        };

        try {
            return astring[(int)(System.nanoTime() % astring.length)];
        } catch (Throwable throwable) {
            return "Witty comment unavailable :(";
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 1 ? suggestMatching(args, "start", "stop") : null;
    }
}
