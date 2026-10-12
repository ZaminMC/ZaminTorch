package net.minecraft.server.command;

import java.util.List;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ParticleCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "particle";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.particle.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 8) {
            throw new IncorrectUsageException("commands.particle.usage");
        }

        boolean flag = false;
        ParticleType particletype = null;

        for (ParticleType particletype1 : ParticleType.values()) {
            if (particletype1.hasParameters()) {
                if (args[0].startsWith(particletype1.getKey())) {
                    flag = true;
                    particletype = particletype1;
                    break;
                }
            } else if (args[0].equals(particletype1.getKey())) {
                flag = true;
                particletype = particletype1;
                break;
            }
        }

        if (!flag) {
            throw new CommandException("commands.particle.notFound", args[0]);
        }

        String s = args[0];
        Vec3d vec3d = source.getCommandSourcePos();
        double d6 = (float)parseCoordinate(vec3d.x, args[1], true);
        double d0 = (float)parseCoordinate(vec3d.y, args[2], true);
        double d1 = (float)parseCoordinate(vec3d.z, args[3], true);
        double d2 = (float)parseDouble(args[4]);
        double d3 = (float)parseDouble(args[5]);
        double d4 = (float)parseDouble(args[6]);
        double d5 = (float)parseDouble(args[7]);
        int i = 0;
        if (args.length > 8) {
            i = parseInt(args[8], 0);
        }

        boolean flag1 = false;
        if (args.length > 9 && "force".equals(args[9])) {
            flag1 = true;
        }

        World world = source.getCommandSourceWorld();
        if (world instanceof ServerWorld) {
            ServerWorld serverworld = (ServerWorld)world;
            int[] aint = new int[particletype.getParameterCount()];
            if (particletype.hasParameters()) {
                String[] astring = args[0].split("_", 3);

                for (int j = 1; j < astring.length; j++) {
                    try {
                        aint[j - 1] = Integer.parseInt(astring[j]);
                    } catch (NumberFormatException numberformatexception) {
                        throw new CommandException("commands.particle.notFound", args[0]);
                    }
                }
            }

            serverworld.addParticle(particletype, flag1, d6, d0, d1, i, d2, d3, d4, d5, aint);
            sendSuccess(source, this, "commands.particle.success", s, Math.max(i, 1));
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, ParticleType.getBasicTypes());
        } else if (args.length > 1 && args.length <= 4) {
            return suggestCoordinate(args, 1, pos);
        } else {
            return args.length == 10 ? suggestMatching(args, "normal", "force") : null;
        }
    }
}
