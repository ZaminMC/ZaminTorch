package net.minecraft.server.command;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.border.WorldBorder;

public class WorldBorderCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "worldborder";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.worldborder.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new IncorrectUsageException("commands.worldborder.usage");
        }

        WorldBorder worldborder = this.getWorldBorder();
        if (args[0].equals("set")) {
            if (args.length != 2 && args.length != 3) {
                throw new IncorrectUsageException("commands.worldborder.set.usage");
            }

            double d0 = worldborder.getSizeLerpTarget();
            double d2 = parseDouble(args[1], 1.0, 6.0E7);
            long i = args.length > 2 ? parseLong(args[2], 0L, 9223372036854775L) * 1000L : 0L;
            if (i > 0L) {
                worldborder.setSize(d0, d2, i);
                if (d0 > d2) {
                    sendSuccess(
                        source,
                        this,
                        "commands.worldborder.setSlowly.shrink.success",
                        String.format("%.1f", d2),
                        String.format("%.1f", d0),
                        Long.toString(i / 1000L)
                    );
                } else {
                    sendSuccess(
                        source,
                        this,
                        "commands.worldborder.setSlowly.grow.success",
                        String.format("%.1f", d2),
                        String.format("%.1f", d0),
                        Long.toString(i / 1000L)
                    );
                }
            } else {
                worldborder.setSize(d2);
                sendSuccess(source, this, "commands.worldborder.set.success", String.format("%.1f", d2), String.format("%.1f", d0));
            }
        } else if (args[0].equals("add")) {
            if (args.length != 2 && args.length != 3) {
                throw new IncorrectUsageException("commands.worldborder.add.usage");
            }

            double d4 = worldborder.getLerpSize();
            double d8 = d4 + parseDouble(args[1], -d4, 6.0E7 - d4);
            long i1 = worldborder.getLerpTime() + (args.length > 2 ? parseLong(args[2], 0L, 9223372036854775L) * 1000L : 0L);
            if (i1 > 0L) {
                worldborder.setSize(d4, d8, i1);
                if (d4 > d8) {
                    sendSuccess(
                        source,
                        this,
                        "commands.worldborder.setSlowly.shrink.success",
                        String.format("%.1f", d8),
                        String.format("%.1f", d4),
                        Long.toString(i1 / 1000L)
                    );
                } else {
                    sendSuccess(
                        source,
                        this,
                        "commands.worldborder.setSlowly.grow.success",
                        String.format("%.1f", d8),
                        String.format("%.1f", d4),
                        Long.toString(i1 / 1000L)
                    );
                }
            } else {
                worldborder.setSize(d8);
                sendSuccess(source, this, "commands.worldborder.set.success", String.format("%.1f", d8), String.format("%.1f", d4));
            }
        } else if (args[0].equals("center")) {
            if (args.length != 3) {
                throw new IncorrectUsageException("commands.worldborder.center.usage");
            }

            BlockPos blockpos = source.getCommandSourceBlockPos();
            double d1 = parseCoordinate(blockpos.getX() + 0.5, args[1], true);
            double d3 = parseCoordinate(blockpos.getZ() + 0.5, args[2], true);
            worldborder.setCenter(d1, d3);
            sendSuccess(source, this, "commands.worldborder.center.success", d1, d3);
        } else if (args[0].equals("damage")) {
            if (args.length < 2) {
                throw new IncorrectUsageException("commands.worldborder.damage.usage");
            }

            if (args[1].equals("buffer")) {
                if (args.length != 3) {
                    throw new IncorrectUsageException("commands.worldborder.damage.buffer.usage");
                }

                double d5 = parseDouble(args[2], 0.0);
                double d9 = worldborder.getSafeZone();
                worldborder.setSafeZone(d5);
                sendSuccess(source, this, "commands.worldborder.damage.buffer.success", String.format("%.1f", d5), String.format("%.1f", d9));
            } else if (args[1].equals("amount")) {
                if (args.length != 3) {
                    throw new IncorrectUsageException("commands.worldborder.damage.amount.usage");
                }

                double d6 = parseDouble(args[2], 0.0);
                double d10 = worldborder.getDamagePerBlock();
                worldborder.setDamagePerBlock(d6);
                sendSuccess(source, this, "commands.worldborder.damage.amount.success", String.format("%.2f", d6), String.format("%.2f", d10));
            }
        } else if (args[0].equals("warning")) {
            if (args.length < 2) {
                throw new IncorrectUsageException("commands.worldborder.warning.usage");
            }

            int j = parseInt(args[2], 0);
            if (args[1].equals("time")) {
                if (args.length != 3) {
                    throw new IncorrectUsageException("commands.worldborder.warning.time.usage");
                }

                int k = worldborder.getWarningTime();
                worldborder.setWarningTime(j);
                sendSuccess(source, this, "commands.worldborder.warning.time.success", j, k);
            } else if (args[1].equals("distance")) {
                if (args.length != 3) {
                    throw new IncorrectUsageException("commands.worldborder.warning.distance.usage");
                }

                int l = worldborder.getWarningDistance();
                worldborder.setWarningDistance(j);
                sendSuccess(source, this, "commands.worldborder.warning.distance.success", j, l);
            }
        } else {
            if (!args[0].equals("get")) {
                throw new IncorrectUsageException("commands.worldborder.usage");
            }

            double d7 = worldborder.getLerpSize();
            source.addResult(CommandResults.Type.QUERY_RESULT, MathHelper.floor(d7 + 0.5));
            source.sendMessage(new TranslatableText("commands.worldborder.get.success", String.format("%.0f", d7)));
        }
    }

    protected WorldBorder getWorldBorder() {
        return MinecraftServer.getInstance().worlds[0].getWorldBorder();
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, "set", "center", "damage", "warning", "add", "get");
        } else if (args.length == 2 && args[0].equals("damage")) {
            return suggestMatching(args, "buffer", "amount");
        } else if (args.length >= 2 && args.length <= 3 && args[0].equals("center")) {
            return suggestHorizontalCoordinate(args, 1, pos);
        } else {
            return args.length == 2 && args[0].equals("warning") ? suggestMatching(args, "time", "distance") : null;
        }
    }
}
